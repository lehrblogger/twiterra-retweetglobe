/*
 * TwiTerra
 *   Revealing how people use Twitter to share and re-share ideas, building connections that encircle the world.
 *   http://twiterra.com
 *   http://github.com/lehrblogger/twiterra-retweetglobe/
 * project by Steven Lehrburger
 *   lehrburger (at) gmail (dot) com
 * NYU Interactive Telecommunications Program, Fall 2008
 * Introduction to Computational Media with Dan Shiffman
 * 
 * NASA World Wind code
 * Copyright (C) 2001, 2006 United States Government
 * as represented by the Administrator of the
 * National Aeronautics and Space Administration.
 * All Rights Reserved.
 */
package com.twiterra.retweetglobe

import scala.actors.Actor
import net.liftweb.util.{Log, Box, Full, Empty}
import net.liftweb.mapper._
import java.sql.{Connection, DriverManager, SQLException}
import net.liftweb.mapper.Schemifier

// Handles establishing the database connection, getting new tweets from the database,
 // and sending to the globe to be displayed
class TweetHandler (
	val globeActor: Actor			   // needs this to send ready-to-display tweets to the globe
  ) {

  // a few constants that determine visualization behavior 
    // note tha the last three require the expensive recursivelyPopulateChildList
  val queueSize = 20                    // number of simultaneous database query threads
  val minNumRetweets = 4                // mininum number of retweets in each tree
  val minDepth = 2                      // minimum depth of each tree
  val minAvgDist = 3000                 // miniumum average geographic distance for the tree
  val minDist = 0                       // minimum geographic distance between any two nodes

    
                                        // first, initialize the database connection
  println("Initializing database connection")
  DB.defineConnectionManager(DefaultConnectionIdentifier, DBVendor)
  println("Connection defined")
  Schemifier.schemify(false, Log.infoF _, Tweet)
  print("Table schemified")
  var numRootTweets = Tweet.count(NullRef(Tweet.parentId), 
                                  By_>(Tweet.numRetweets, minNumRetweets))
                                         // second, find out how many trees to display before loooping
  var lastParentId = Tweet.find(NullRef(Tweet.parentId), 
                                OrderBy(Tweet.tweetId, Descending), 
                                MaxRows(1)).open_!.tweetId.is
                                         // third, find the last ID of the last tweet, so we can start looking for new ones 
  println(" with numRootTweets=" + numRootTweets + " and lastParentId=" + lastParentId)
  
  var index: Int = -1       			 // a counter to keep track of how far it is through the list              
                                           // is incremented immediately, so starts off one less
  var curNumThreads = 0                  // how many database query theads are currently open
  var gettingNewTweets: Boolean = false  // again, somewhat bad style - a threading lock so that only
                                           // thread looks for new tweets at a time
  var newTweets: List[Tweet] = Nil       // the list of new tweets is stored, emptied, and then gotten again
                                           // rather than gotten again for every single tweet
                                           // slightly more efficient with database calls than before
  
  // Adds tweets to the queue until it reaches the maximum by firing off new database query threads
  def addTweetsToQueue = {
    for (i <- curNumThreads to queueSize) {
      addOneTweetToQueue
    }
  }
  
  // Adds one tweet to the queue by firing off a new database query thread
  def addOneTweetToQueue = {      
    println("globeActor.mailboxSize = " + globeActor.mailboxSize + "  curNumThreads = " + curNumThreads)
    if (curNumThreads < queueSize) {
      val newDbActor = new dbActor() // somewhat unnecessary, but check to make sure there aren't
      newDbActor.start()                   // too many threads before starting a new one
    }
  }
  
  // Resets the index and recalculates how many there are total
    // (since any new tweets from before should now be considered old)
  def resetIndex {
    index = 0 
    numRootTweets = Tweet.count(NullRef(Tweet.parentId), By_>(Tweet.numRetweets, minNumRetweets))
  }
  

  // An extension of the Actor class to handle database queries - made more sense to keep it here
    // It needs access to many of the 
  class dbActor() extends Actor {
    def act() { 
      println("starting one new thread + newTweets.length=" + newTweets.length)
      index += 1                       // increment the index to the next root tweet
                                         // this has to happen right at the beginning, so that
                                         // each thread is using a different index! otherwise
                                         // the same tweet appears repeatedly
      var i = index                  // *and* we need to grab the current index, because soon it 
                                         // will be increased again. an obvious solution to this
                                         // messiness would just be to pass and increment the index
                                         // from the function in TweetHandler creating the Actor,
                                         // but that doesn't work because we don't *always*
                                         // increment it. so how can this be more elegant?
      curNumThreads += 1               // the thread is starting, so increment the count
   
      // first, check for new tweets
      if (!gettingNewTweets && (newTweets.length == 0)) {
        gettingNewTweets = true        // we only want one thread checking for new tweets at once
        newTweets = newTweets ++ Tweet.findAll(NullRef(Tweet.parentId), 
                                               // parentId has to be NULL, since we want roots
                                               By_>(Tweet.tweetId, lastParentId), 
                                               // it must have happened after the last parent
                                               OrderBy(Tweet.tweetId, Ascending))
                                               // and we want to look at the earliest ones first
        if (newTweets.length > 0) lastParentId = newTweets.last.tweetId  
                                       // if we found new tweets, reset the last parentID
        gettingNewTweets = false       // and free the lock
      }
      
      // if there are new tweets, display them instead of the old ones
      if (newTweets.length > 0) {
        index -= 1                     // decrement index back if not actually getting the next one
          
        var newTweet = newTweets.first
        newTweets = newTweets.drop(1)  // remove the first new tweet from the list
        
        newTweet.recursivelyPopulateChildList // see notes in Tweet.scala
        println("  sendTweet (new) " + lastParentId + "  from " + newTweet.author)
        globeActor ! Pair("incoming new tweet", newTweet)
                                       // send it to the globe to be displayed
                                       // never trash the new tweets! they are too precious

      // if there are no new tweets, display the next old tweet
      } else if (i < numRootTweets) {
        var oldTweet = Tweet.find(StartAt(i),         // get the next one
                                  MaxRows(1),             // only get one 
                                  NullRef(Tweet.parentId),// only get roots, and 
                                                          // make sure it has enough retweets
                                  By_>(Tweet.numRetweets, minNumRetweets)).open_!
        oldTweet.recursivelyPopulateChildList
      
        if (treeIsAcceptable(oldTweet)) {
          println("  sendTweet (old interesting) " + i + "  from " + oldTweet.author)
          globeActor ! Pair("incoming old interesting tweet", oldTweet)
                                       // send it to the globe to be displayed
        } else {                       // but still send it if it is less good, just keep it at the back of the queue
          if (globeActor.mailboxSize < (queueSize / 5)) {
                                       // but also don't let the uninteresting ones fill up the entire queue
            globeActor ! Pair("incoming old uninteresting tweet", oldTweet)
            println("  sendTweet (old uninteresting) " + i + "  from " + oldTweet.author)
          } else {
            println("  enough tweets in queue, trashing " + i + "  from " + oldTweet.author)
          }
        }
      
      } else { resetIndex }            // otherwise we are at the end, and can restart from the beginning
      
      curNumThreads -= 1               // the thread is about to finish, so decrement the count
      addTweetsToQueue                 // TODO maybe this shouldn't get called so many times semi-
    }                                    // recursively, but it works and doesn't break... 
  
    // Just to simplify the above if statements, and give feedback about what sort of trees,
      // if any, are being thrown away
    def treeIsAcceptable(t: Tweet): Boolean = {
      println("    " + t.author                + 
              "  t.depth=" + t.depth.toInt     + 
              " minAvgDist=" + t.avgDist.toInt +
              " minDist=" + t.minDist.toInt    )
      ((t.numRetweets > minDepth) &&
       (t.depth >= minDepth)      && 
       (t.avgDist >= minAvgDist)  &&
       (t.minDist >= minDist      ))
    }
  }
}
  
// TODO properly comment database stuff
object DBVendor extends ConnectionManager {
  def newConnection(name: ConnectionIdentifier): Box[Connection] = {
    try {
      Class.forName("com.mysql.jdbc.Driver")				//Note that the MySQL username and password are contained below
      val dm = DriverManager.getConnection("jdbc:mysql://mysql.lehrblogger.com/retweettree?user=twiterra_app&password=jelf7ya9head8w")
      Full(dm)
    } catch {
      case e : Exception => e.printStackTrace; Empty
    }
  }
  
  def releaseConnection(conn: Connection) {conn.close}
}
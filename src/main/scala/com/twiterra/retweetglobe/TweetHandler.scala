package com.twiterra.retweetglobe

import scala.actors.Actor
import net.liftweb.util.{Log, Box, Full, Empty}
import net.liftweb.mapper._
import java.sql.{Connection, DriverManager, SQLException}
import net.liftweb.mapper.Schemifier


class TweetHandler (
	val globeActor: Actor	
  ) {
  
  println("Initializing database connection")
  DB.defineConnectionManager(DefaultConnectionIdentifier, DBVendor)
  println("Connection defined")
  Schemifier.schemify(false, Log.infoF _, Tweet)
  println("Table schemified")
  
  var numRootTweets = Tweet.count(NullRef(Tweet.parentId), By_>(Tweet.numRetweets, 2))
  var lastParentId = Tweet.find(NullRef(Tweet.parentId), OrderBy(Tweet.tweetId, Descending), MaxRows(1)).open_!.tweetId.is
  var index: Int = -1
  var queueSize = 30
  var curNumThreads = 0
  var gettingNewTweets: Boolean = false
  var newTweets: List[Tweet] = Nil
  
  def addTweetsToQueue = {
    for (i <- (globeActor.mailboxSize + curNumThreads) to queueSize) {
      addOneTweetToQueue
    }
  }
  
  def addOneTweetToQueue = {      
    println("globeActor.mailboxSize = " + globeActor.mailboxSize + "  curNumThreads = " + curNumThreads)
    if ((globeActor.mailboxSize + curNumThreads) < queueSize) {
      val newDbActor = new dbActor(this)
      newDbActor.start()
    }
  }
  
  def resetIndex { index = 0 }
}

  class dbActor(val h: TweetHandler) extends Actor {
    def act() { 
      println("one new thread")
      h.index += 1 //TODO do index stuff in loop!
	  val i: Int = h.index
      h.curNumThreads += 1
   
      if (!h.gettingNewTweets && (h.newTweets.length == 0)) {
        h.gettingNewTweets = true
        h.newTweets = h.newTweets ++ Tweet.findAll(NullRef(Tweet.parentId), By_>(Tweet.tweetId, h.lastParentId), OrderBy(Tweet.tweetId, Ascending))
	    h.lastParentId = h.newTweets.last.tweetId  
        h.gettingNewTweets = false
      }
      
      if (h.newTweets.length > 0) {
	    h.index -= 1
        var newTweet = h.newTweets.first
        h.newTweets = h.newTweets.drop(1)
        
	    newTweet.recursivelyPopulateChildList
	    println("  sendTweet (new) " + h.lastParentId + "  from " + newTweet.author)
	    h.globeActor ! Pair("incoming new tweet", newTweet)
	    
      } else if (i < h.numRootTweets) {
	    var oldTweet = Tweet.find(StartAt(i), MaxRows(1), NullRef(Tweet.parentId), By_>(Tweet.numRetweets, 2)).open_!
	    oldTweet.recursivelyPopulateChildList
	    if (treeIsAcceptable(oldTweet)) {
	      println("  sendTweet (old) " + i + "  from " + oldTweet.author)
          h.globeActor ! Pair("incoming old tweet", oldTweet)
	    } else {
	      h.addOneTweetToQueue //replensih queue if one was thrown out
	      println("  trashed (old) " + i + "  from " + oldTweet.author)
	    }
     
	  } else {
	    h.resetIndex
	  }
      
      h.curNumThreads -= 1
      h.addTweetsToQueue //TODO maybe this is getting called a million times recursively, and that's causing the out-of-memory crashes
    } 
    
    def treeIsAcceptable(t: Tweet): Boolean = {
      var minDepth = 2
      var minAvgDist = 2000
      var minDist = 300
      println("    " + t.author + "  t.depth=" + t.depth.toInt + " minAvgDist=" + t.avgDist.toInt + " minDist=" + t.minDist.toInt)

      ((t.numRetweets > minDepth) && (t.depth >= minDepth) && (t.avgDist >= minAvgDist) && (t.minDist >= minDist))
    }
  }
  
  
object DBVendor extends ConnectionManager {
  def newConnection(name: ConnectionIdentifier): Box[Connection] = {
    try {
      Class.forName("com.mysql.jdbc.Driver")
      val dm = DriverManager.getConnection("jdbc:mysql://mysql.lehrblogger.com/retweettree?user=twiterra_app&password=jelf7ya9head8w")
      Full(dm)
    } catch {
      case e : Exception => e.printStackTrace; Empty
    }
  }
  
  def releaseConnection(conn: Connection) {conn.close}
}
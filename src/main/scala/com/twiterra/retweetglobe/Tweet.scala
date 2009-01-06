package com.twiterra.retweetglobe

import net.liftweb.mapper._
import net.liftweb.mapper.MappedDouble 
import net.liftweb.util.{Box, Full, Empty}
import java.sql.Timestamp
import java.util.Locale

// Keeps track of all of the data for a single Tweet, including information about its children
// TODO properly comment database stuff
object Tweet extends Tweet with KeyedMetaMapper[Long, Tweet] { override def dbTableName = "tweets" }
class Tweet extends KeyedMapper[Long, Tweet] {
  def getSingleton = Tweet
  def primaryKeyField = tweetId
  
  object tweetId extends MappedLongIndex(this) { override def dbColumnName = "tweet_id" }
  object author extends MappedString(this, 15)
  object original extends MappedString(this, 140)
  object locLat extends MappedDouble(this) { override def dbColumnName = "loc_lat" }
  object locLon extends MappedDouble(this) { override def dbColumnName = "loc_lon" }
  object time extends MappedString(this, 20) { override def dbColumnName = "time" }
  object numRetweets extends MappedLong(this) { override def dbColumnName = "num_retweets" }
  object parentId extends MappedLongForeignKey(this, Tweet) { override def dbColumnName = "parent_id" }
  object parentDist extends MappedDouble(this) { override def dbColumnName = "parent_dist" }
  
  var children: List[Tweet] = Nil               // this will stay Nil if there are no children
  var depth: Int = 0                              // and this will stay 0

  // Fills the child list - a somewhat expensive database call, so use sparingly
  def getChildren = Tweet.findAll(By(Tweet.parentId, tweetId), MaxRows(numRetweets))
  
  // Returns a flattened list of all of the descendants
  def descendants: List[Tweet] = children ++ children.flatMap(_.descendants)

  // Recursively opulates the list of children from the database and
    // calculates the depth for each tweet on the way back up
    // THIS MUST BE CALLED BEFORE DOING ANYTHING WITH THE TWEET
    // But it is expensive, so we don't want to call it in the constructor, especially for 
    // the new tweets, which are found in a batch but sent one by one
  def recursivelyPopulateChildList: Unit = {
    if (numRetweets > 0) {                      // if there are no retweets, there are no children
      children = getChildren                    // this is the expensive database call
      var childDepths: List[Int] = Nil          // we'll need to temporarily keep track of these
      children.foreach(child => {               // iterate through the children
        child.recursivelyPopulateChildList      // recurse
        childDepths = childDepths ++ List(child.depth) // and update the list of child depths
      })
      depth = childDepths.sort(_>_).first + 1   // the depth for *this* tweet is 1 + the depth of
    }                                             // it's deepest child
  }
 
  // Used for determining which child to follow, this evaluates children based on depth and distance
  def indexOfMostInterestingChild:Int = {
    if (children.length <= 0) return -1         // the -1 is somewhat bad style, but simplifies
                                                  // things later, and isn't tooo dangerous

    // a lot happens here:
      // first, group each child with its depth and avgDist, and store those in a list of tuples
    val list1 = for ((t, index) <- children.zipWithIndex) yield (index, t.depth, t.avgDist)
      // second, sort that by depth - we want the camera to follow the branch animated for longest
    val list2 = list1.sort(_._2 > _._2)
      // third, get the child with the greatest depth (more than one in the case of a tie)
    val list3 = list2.filter(each => each._2 >= list2.first._2) 
      // finally, sort that based on their depth, and return the index of the deepest of them
    list3.sort(_._3 > _._3).first._1
  }
  
  // Determines the minimum geographic distance between any two tweet nodes in the tree
  def minDist = {
    var min = Math.MAX_DOUBLE
    descendants.foreach(t => {                  // uses the flattened list of children
      if (t.parentDist < min) min = t.parentDist
    })
    min                                         // (specify the return value)
  }
  
  // Calculates the average distance between all tweet nodes in this tree
  def avgDist = {
    // First, add each element of the list to the sum of the previous elements
    // And then divide by the length, or by 1, to avoid ever dividing by 0
    descendants.foldLeft(0.0)(_ + _.parentDist.is) / (descendants.length max 1)	
  }
  
  // Formats the author and tweet text as a single string to be sent to a GlobeAnnotation
  override def toString = {
    println(author + ": " + original)
    author + ": " + new String(original.getBytes(), "utf8")	
            								  	// without this recreation of the original string,
                                                  // it will crash on certain character sets
                                                  // now it will display them as diamonds with ?marks
  }                                               // TODO make it work with all characters!   
}
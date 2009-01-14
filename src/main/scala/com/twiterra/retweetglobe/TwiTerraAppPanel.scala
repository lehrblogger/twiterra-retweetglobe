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

import gov.nasa.worldwind.util.StatusBar
import gov.nasa.worldwind.avlist._
import gov.nasa.worldwind._
import gov.nasa.worldwind.geom._
import gov.nasa.worldwind.render._
import gov.nasa.worldwind.globes._
import gov.nasa.worldwind.layers._	
import gov.nasa.worldwind.awt._
import gov.nasa.worldwind.examples.LineBuilder
import gov.nasa.worldwind.view.ScheduledOrbitViewStateIterator
import gov.nasa.worldwind.view.BasicOrbitView

import javax.swing._
import java.awt._
import javax.media.opengl.GLContext
import java.util.Random
import java.util.{ArrayList => JArrayList}
import java.lang.{Iterable => JIterable}
import java.util.{Iterator => JIterator}
import java.util.{List => JList}
import java.util.{Collection => JCollection}

import scala.actors._ 
import scala.actors.Actor._
import scala.collection.jcl.Conversions._
import scala.collection.jcl._

import org.jdesktop.animation.timing.{Animator, TimingTargetAdapter}
import org.jdesktop.animation.timing.interpolation.PropertySetter

// This class contains the actual globe. It is initialized by the main class, and runs everything
class TwiTerraAppPanel (
  val canvasSize: Dimension
) extends JPanel {
  
  // this was existing/slightly adapted WW stuff
  // I spent a long time looking through the docs to get it all working,
  // but haven't needed to touch it since then
  var wwd: WorldWindowGLCanvas = new WorldWindowGLCanvas
  wwd.setPreferredSize(canvasSize)
  var m: Model = (WorldWind.createConfigurationComponent(AVKey.MODEL_CLASS_NAME)).asInstanceOf[Model];
  wwd.setModel(m);
  add(this.wwd, BorderLayout.CENTER)
  var context = wwd.getSceneController.getDrawContext
  context.setModel(m)
  context.setSurfaceGeometry(new SectorGeometryList)
  context.setGLContext(wwd.getContext)
    
  // this goes through all the layers, keeping only the ones we will need
  val layers: scala.List[Layer] = wwd.getModel.getLayers.toList.filter { l => (
    l.isInstanceOf[gov.nasa.worldwind.layers.StarsLayer] ||
    l.isInstanceOf[gov.nasa.worldwind.layers.SkyGradientLayer] ||
    l.isInstanceOf[gov.nasa.worldwind.layers.FogLayer] ||
    l.isInstanceOf[gov.nasa.worldwind.layers.Earth.BMNGOneImage] ||
    l.isInstanceOf[gov.nasa.worldwind.layers.Earth.BMNGWMSLayer] ||
    //l.isInstanceOf[gov.nasa.worldwind.layers.Earth.LandsatI3WMSLayer] ||
    //l.isInstanceOf[gov.nasa.worldwind.layers.Earth.NAIPCalifornia] ||
    //l.isInstanceOf[gov.nasa.worldwind.layers.Earth.USGSUrbanAreaOrtho] ||
    //l.isInstanceOf[gov.nasa.worldwind.layers.Earth.EarthNASAPlaceNameLayer] ||
    //l.isInstanceOf[gov.nasa.worldwind.layers.WorldMapLayer] ||
    //l.isInstanceOf[gov.nasa.worldwind.layers.ScalebarLayer] ||
    //l.isInstanceOf[gov.nasa.worldwind.layers.CompassLayer] ||
    false // for testing purposes, so you can comment them all 
  )}

  val initLayerCount = layers.length         // take note of the initial number of layers
  wwd.getModel.setLayers(new LayerList(layers.toArray)) // and set them in the model

  // The globeActor displays tweets on the globe
  val globeActor = actor {
    loop {
      react {
        case "animation complete" => react { // this means that the previous animation is done
                                             // and the globe can display a new one
          case ("incoming new tweet", newTweet: Tweet) => displayTweetTree(newTweet, true)
                                             // so if it has received one, display it
          case ("incoming old interesting tweet", newTweet: Tweet) => displayTweetTree(newTweet, false)
                                             // being sure to give preference to the new tweets
          case ("incoming old uninteresting tweet", newTweet: Tweet) => displayTweetTree(newTweet, false)
                                             // being sure to give preference to the new tweets
        }
      }
    }
  }
  
  globeActor ! "animation complete"          // tell the globeActor that the animation is complete
                                             // so that it is ready to display the first tree
  val tweetHandler = new TweetHandler(globeActor)
  tweetHandler.addTweetsToQueue              // make the tweetHandler and start off the DB query threads
      
  val maxNumTrees = 7                        // how many trees to display at a time 
                                               // (too many and it gets slow and cluttered)
  val animDuration = 3250                    // this controls the speed of the animations, in ms
  val readDuration = 4750                    // and the length of the pause to read them
   
  // The transitionActor handles the pauses for reading at each level of each tree
  val transitionActor = actor {
    loop {
      react { 
        case t: TweetPackage => {            // when it gets a new tweet to display
          Thread.sleep(readDuration)         // pause to read the old one
          displayTweetChildren(t)            // and then display the children of the new one
        }
        case "animation complete" => {       // this means a whole tree is done
          tweetHandler.addTweetsToQueue      // make sure the queue of DB query threads is full
                                             // since one just finished (prob unnecessary, can't hurt)
          globeActor ! "animation complete"  // tell the globe animation to display a new tree
        }
      }
    }
  }
    
  // This displays an entire tree, starting with the root
  def displayTweetTree(newTweet: Tweet, isNewTweet: Boolean): Unit = {          
    // first, find out where the camera is and find out where the camera is going
    val initEyePos: Position = new Position(wwd.getView.getCurrentEyePosition.getLatitude, 
                                            wwd.getView.getCurrentEyePosition.getLongitude, 0)
    val newTweetPos: Position = Position.fromDegrees(newTweet.locLat,
                                                     newTweet.locLon, 0)
    // and then move the camera using the built-in view state iterator
    wwd.getView.applyStateIterator(ScheduledOrbitViewStateIterator.createCenterIterator(initEyePos, newTweetPos, animDuration, true))
    // but wait until it gets there before doing anything else
    Thread.sleep(animDuration)
	    
    // choose a random color for this tweet, within a certain range
    val random = new Random
    def randomColor = (random.nextFloat * 65).toInt + 175
    val color = new Color(randomColor, randomColor, randomColor)
	  
    // decrease the opaciies of all previously displayed trees that are still visible
    updateTreeLayerOpacities
    
    // create the layer for the renderables that will be displayed, and add them to the globe
    val rLayer: RenderableLayer = new RenderableLayer()
    wwd.getModel.getLayers.add(wwd.getModel.getLayers.size, rLayer)
    
    // create the annotation for the root tweet
    val tweetAnno = new TweetAnnotation(newTweet.toString, Position.fromDegrees(newTweet.locLat, newTweet.locLon, 0), color, true, isNewTweet)
    // create the layer for it, add it to the layer, and add it to the globe
    val aLayer: AnnotationLayer = new AnnotationLayer()
    aLayer.addAnnotation(tweetAnno)
    wwd.getModel.getLayers.add(wwd.getModel.getLayers.size, aLayer)
	    
    // put all of this data in a tweet package, and send it to the transition actor
    // which will pause to give time to read the tweet, and then display it's children recursively
    transitionActor ! new TweetPackage(newTweet, true, isNewTweet, rLayer, aLayer, color)
  }
    
  // This function displays all of the children of a specific tweet
  // It is recursive, although this recursion is somewhat obscured by the various actors/tweens
  def displayTweetChildren(t: TweetPackage): Unit = {
    val newPos: Position = Position.fromDegrees(t.tweet.locLat, t.tweet.locLon, 0)
    
    // this is the child we are going to follow with the camera
    var maxIndex = t.tweet.indexOfMostInterestingChild
    
    var index = 0 // TODO change this to be a zip-with-index Scala loop  
    t.tweet.children.foreach(childTweet => {
      // get the position of the child
      val childPos: Position = Position.fromDegrees(childTweet.locLat, childTweet.locLon, 0)
      
      // determine if it is to be followed (utility variable to simplify code later)
      val followNext = (t.followThis && (maxIndex == index))
      
      // make the annotation and the line for the tweet, and add them their respective layers
      val tweetAnno = new TweetAnnotation(childTweet.toString, newPos, t.color, followNext, t.isNewTweet)
      t.aLayer.addAnnotation(tweetAnno)
      var line = new AnimatedAnnotatedLine(newPos, childPos, tweetAnno, t.color, followNext)
      t.rLayer.addRenderable(line)
        
      // make a new tweening LineEventHandler to handle the animation, and start it
      // (when it finishes, it will trigger the recursion)
      val target = new LineEventHandler(line, new TweetPackage(childTweet, followNext, t.isNewTweet, t.rLayer, t.aLayer, t.color))
      val anim: Animator = new Animator(animDuration, target)
      anim.start()
        
      // if we are following this tweet, have the camera move too
      if (followNext) {
        wwd.getView.applyStateIterator(ScheduledOrbitViewStateIterator.createCenterIterator(newPos, childPos, animDuration, true))
      }
      
      index += 1
    })
      
    // if we ere following this with the camera, and there are no more children on this path
    // then, since we always follow the deepest branch, we know we are done 
    // and can signal that we want to display a new tweet
    if (t.followThis && (t.tweet.children.length == 0)) {
      transitionActor ! "animation complete"
    }
  }
   
  // This updates the opacities of the trees on each layer displayed on the globe so that 
    // trees become increasingly transparent as they become older and are replaced by new trees
    // it also makes sure that there aren't too many layers trying to be displayed at a time
  def updateTreeLayerOpacities = {
    var initLayers: scala.List[Layer] = wwd.getModel.getLayers.toList
                                             // get the current layers and keep them in a list
    var finalLayers: scala.List[Layer] = initLayers.dropRight(initLayers.length - initLayerCount)
                                             // make a new list of all of the initial layers to keep
    
    var alpha = (255 / maxNumTrees)          // the alphas decrease more gradually when there are
                                               // more trees being displayed at a time
    initLayers = initLayers.drop(initLayerCount)// then drop the initial layers
                                               // since we already saving them in finalLayers          
    initLayers = initLayers.drop(initLayers.length - (maxNumTrees.toInt * 2))
                                             // and drop any layers that won't be kept at all
                                               // maxNumTrees * 2 because each tree has 2 layers
    initLayers.foreach( l => {               // then, for each of the remaining layer
      if (l.isInstanceOf[RenderableLayer]) { // decrease their alpha - the setLayerOpacity
        setRLayerOpacity(l.asInstanceOf[RenderableLayer], alpha) // functions will be cumulative
      } else if (l.isInstanceOf[AnnotationLayer]) {
        setALayerOpacity(l.asInstanceOf[AnnotationLayer], alpha)
      }
        finalLayers = finalLayers ++ scala.List(l) 
    })                                       // be sure to add the layer to the final list to keep them
    
    wwd.getModel.setLayers(new LayerList(finalLayers.toArray))
  }
   
  // This sets the opacity for a Renderable Layer (for Polylines)
  def setRLayerOpacity(l: RenderableLayer, alpha: Int) {
    var renderablesIterator: JIterator[Renderable] = l.getRenderables.iterator
   
    while (renderablesIterator.hasNext) { // iterate through each renderable on the layer, 
      var r = renderablesIterator.next      // updating the opactiy of each one
      if (r.isInstanceOf[AnimatedAnnotatedLine]) {
        r.asInstanceOf[AnimatedAnnotatedLine].updateLineOpacity(alpha)
      }
    }
  }
  
  // This sets the opacity for a Annotations Layer
  def setALayerOpacity(l: AnnotationLayer, alpha: Int) {
    var annotationsIterator: JIterator[Annotation] = l.getAnnotations.iterator
   
    while (annotationsIterator.hasNext) { // iterate through each renderable on the layer, 
      var a = annotationsIterator.next      // updating the opactiy of each one
      if (a.isInstanceOf[TweetAnnotation]) {
        a.asInstanceOf[TweetAnnotation].updateAnnotationOpacity(alpha)
      }
    }
  }

  // This is needed by the Java main class
  def getWwd: WorldWindowGLCanvas = {
    return wwd;
  }
    
  // This class handles the tweening of the lines and annotations
    // It uses the TimingFramework libraries to tell the line to update at a consistent rate
  class LineEventHandler(
    line: AnimatedAnnotatedLine, // the line being drawn by this tween
    t: TweetPackage              // the tweet with the information for the rest of this branch of the tree
  ) extends TimingTargetAdapter {
 
    override def begin = {}
      
    override def timingEvent(fraction: Float) = {
      line.updateLine(fraction)	 // update the line to the current fraction with every timing event
    }                            // see notes in AnimatedAnnotatedLine.java
  
    override def end = {         // when the animation completes
      if (t.followThis) {        // if the branch currently being animated was followed
        transitionActor ! t      // then we can signal that we're ready to go to the next level
                                   // after a delay for reading
      } else {                   // otherwise, just display the tweet's children recursively
        displayTweetChildren(t)          
      }
    } 
  }	    
}

// This is a case class to make it easy to pass around all of the data for a single root tweet
case class TweetPackage(tweet: Tweet, 
                        followThis: Boolean, 
                        isNewTweet: Boolean, 
                        rLayer: RenderableLayer, 
                        aLayer: AnnotationLayer, 
                        color: Color)

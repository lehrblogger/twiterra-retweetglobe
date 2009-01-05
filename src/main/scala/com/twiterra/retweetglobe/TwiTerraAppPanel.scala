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


class TwiTerraAppPanel (val canvasSize: Dimension) extends JPanel
{
  var wwd: WorldWindowGLCanvas = new WorldWindowGLCanvas()												// random stuff I do not full understand for the DrawingContext, for the lines and Annotations
  var initLayerCount = 0;
  
  wwd.setPreferredSize(canvasSize);

  // Create the default model as described in the current worldwind properties.
  var m: Model = (WorldWind.createConfigurationComponent(AVKey.MODEL_CLASS_NAME)).asInstanceOf[Model];
  wwd.setModel(m);

  // Setup a select listener for the worldmap click-and-go feature
  //wwd.addSelectListener(new ClickAndGoSelectListener(this.getWwd(), WorldMapLayer.class));

  add(this.wwd, BorderLayout.CENTER)							    // Create World Window GL Canvas
                                                                                            // Create the default model as described in the current worldwind properties.
  var context = wwd.getSceneController.getDrawContext
  context.setModel(m)
  context.setSurfaceGeometry(new SectorGeometryList) 									// spent a long time pouring through docs to find stuff for the DrawingContext that worked
  context.setGLContext(wwd.getContext)
    
      var layers: scala.List[Layer] = wwd.getModel.getLayers.toList
      layers = layers.filter { l =>
	      (l.isInstanceOf[gov.nasa.worldwind.layers.StarsLayer] ||
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
           false //for commenting 
	      )
      }
      initLayerCount = layers.length
      wwd.getModel.setLayers(new LayerList(layers.toArray))
        /*
      val aLayer: AnnotationLayer = new AnnotationLayer()
	  aLayer.addAnnotation(new GlobeAnnotation("我与今天上午离开人世，再见，中国人", Position.fromDegrees(43.7340, 7.4211, 0), Font.decode("Arial-BOLD-12")));
      aLayer.addAnnotation(new GlobeAnnotation("หาเสื้อสี #FF3300 มาใส่ดีกว่า", Position.fromDegrees(43.696, 7.27, 0), Font.decode("Arial-BOLD-12")));
	  wwd.getModel.getLayers.add(wwd.getModel.getLayers.size, aLayer)
     */
     
    val globeActor = actor {
        loop {
          react {
            case "animation complete" => react {
              case ("incoming new tweet", newTweet: Tweet) => {
                displayTweetTree(newTweet, true)
              }
              case ("incoming old tweet", newTweet: Tweet) => {
                displayTweetTree(newTweet, false)
              }
            }
          }
        }
    }
    val tweetHandler = new TweetHandler(globeActor)
    tweetHandler.addTweetsToQueue
   
    globeActor ! "animation complete"
      
    val maxNumTrees = 8
    val animDuration = 3000
    val readDuration = 4500
   
    val transitionActor = actor {
      loop {
        react {
          case t: TweetPackage => {
            Thread.sleep(readDuration)
            displayTweet(t)
          }
          case "animation complete" => {
            tweetHandler.addTweetsToQueue
            globeActor ! "animation complete" //wait until all the animations here are done, then it will wake up and go
          }
        }
      }
    }
    
    
    def displayTweetTree(newTweet: Tweet, isNewTweet: Boolean): Unit = {          
      val initEyePos: Position = new Position(wwd.getView.getCurrentEyePosition.getLatitude, wwd.getView.getCurrentEyePosition.getLongitude, 0)
	    val newTweetPos: Position = Position.fromDegrees(newTweet.locLat, newTweet.locLon, 0)
	    wwd.getView.applyStateIterator(ScheduledOrbitViewStateIterator.createCenterIterator(initEyePos, newTweetPos, animDuration, true))
	    Thread.sleep(animDuration)
	    
	    
	    val randColor = new Random()	
	    val color = new Color((randColor.nextFloat * 65).toInt + 175, (randColor.nextFloat * 65).toInt + 175, (randColor.nextFloat * 65).toInt + 175)
	      
	    val rLayer: RenderableLayer = new RenderableLayer()
	    wwd.getModel.getLayers.add(wwd.getModel.getLayers.size, rLayer)
	  
	    updateTreeLayers
	    val tweetAnno = new TweetAnnotation(newTweet.toString, Position.fromDegrees(newTweet.locLat, newTweet.locLon, 0), color, true, isNewTweet)
	    val aLayer: AnnotationLayer = new AnnotationLayer()
	    aLayer.addAnnotation(tweetAnno)
	    wwd.getModel.getLayers.add(wwd.getModel.getLayers.size, aLayer)
	    //Thread.sleep(readDuration)
	    
	    transitionActor ! new TweetPackage(newTweet, true, isNewTweet, rLayer, aLayer, color)
    }
    
    def displayTweet(t: TweetPackage): Unit = {
      val newPos: Position = Position.fromDegrees(t.tweet.locLat, t.tweet.locLon, 0)
      var maxIndex = t.tweet.indexOfMostInterestingChild
      var index = 0
      
      t.tweet.children.foreach(childTweet => {
        val childPos: Position = Position.fromDegrees(childTweet.locLat, childTweet.locLon, 0)
        val followNext = (t.followThis && (maxIndex == index))
      
        val tweetAnno = new TweetAnnotation(childTweet.toString, newPos, t.color, followNext, t.isNewTweet)
        t.aLayer.addAnnotation(tweetAnno)
        var line = new AnimatedAnnotatedLine(newPos, childPos, tweetAnno, t.color, followNext)
        t.rLayer.addRenderable(line)
        
        val target = new LineEventHandler(line, new TweetPackage(childTweet, followNext, t.isNewTweet, t.rLayer, t.aLayer, t.color))
        val anim: Animator = new Animator(animDuration, target)
        anim.start()
        
     	if (followNext) {
     	  wwd.getView.applyStateIterator(ScheduledOrbitViewStateIterator.createCenterIterator(newPos, childPos, animDuration, true))
        }
      
        index += 1
      })
      
      if (t.followThis && (t.tweet.children.length == 0)) {
        transitionActor ! "animation complete"
      }
    }
    
    def updateTreeLayers = {
      var initLayers: scala.List[Layer] = wwd.getModel.getLayers.toList
      var finalLayers: scala.List[Layer] = initLayers.dropRight(initLayers.length - initLayerCount)
      
      var alpha = (255 / maxNumTrees)
      initLayers = initLayers.drop(initLayerCount)
      initLayers = initLayers.drop(initLayers.length - (maxNumTrees.toInt * 2))
      initLayers.foreach( l => {
        if (l.isInstanceOf[RenderableLayer]) {
          setRLayerOpacity(l.asInstanceOf[RenderableLayer], alpha)
	    } else if (l.isInstanceOf[AnnotationLayer]) {
          setALayerOpacity(l.asInstanceOf[AnnotationLayer], alpha)
	    }
        finalLayers = finalLayers ++ scala.List(l)
      })
      
      wwd.getModel.setLayers(new LayerList(finalLayers.toArray))
    }
    
  def setRLayerOpacity(l: RenderableLayer, alpha: Int) {
	  var renderablesIterator: JIterator[Renderable] = l.getRenderables.iterator
   
	  while (renderablesIterator.hasNext) {
	    var r = renderablesIterator.next
	    if (r.isInstanceOf[AnimatedAnnotatedLine]) {
	      r.asInstanceOf[AnimatedAnnotatedLine].updateLineOpacity(alpha)
	    }
	 }
  }
  def setALayerOpacity(l: AnnotationLayer, alpha: Int) {
	  var renderablesIterator: JIterator[Annotation] = l.getAnnotations.iterator
   
	  while (renderablesIterator.hasNext) {
	    var a = renderablesIterator.next
        if (a.isInstanceOf[TweetAnnotation]) {
	      a.asInstanceOf[TweetAnnotation].updateAnnotationOpacity(alpha)
	    }
	 }
  }
    

  def getWwd: WorldWindowGLCanvas = {
     return wwd;
  }
    
    class LineEventHandler(line: AnimatedAnnotatedLine, t: TweetPackage) extends TimingTargetAdapter
    {
      override def begin = {
      }
      
      override def timingEvent(fraction: Float) = {
        line.updateLine(fraction)
      }
  
      override def end = {
        if (t.followThis) {
          transitionActor ! t
        } else {
          displayTweet(t)
        }
      } 
    }	    
}

case class TweetPackage(tweet: Tweet, followThis: Boolean, isNewTweet: Boolean, rLayer: RenderableLayer, aLayer: AnnotationLayer, color: Color)

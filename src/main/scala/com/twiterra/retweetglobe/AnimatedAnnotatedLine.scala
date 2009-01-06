package com.twiterra.retweetglobe

import gov.nasa.worldwind.render.Polyline
import gov.nasa.worldwind.geom.Position
import java.awt.Color
import java.util.{ArrayList => JArrayList}

// An extension of the built-in Polyline class to support animation of the line and it's annotation
class AnimatedAnnotatedLine (
  val startPos: Position,
  val endPos: Position, 
  val tweetAnno: TweetAnnotation,           // the Polyline needs the Annotation so that it can
                                              // move it along with the end of the line as it tweens
  val color: Color, 
  val isFollowed:Boolean                    // Polylines are displayed differently if they are 
                                              // being followed by the camera
) extends Polyline {
 
  // Configure the display properties for the line
  setHighlighted(true)	
  if (isFollowed) {                           // followed lines are thicker and have a brighter
                                                // highlight on the edges
    setLineWidth(4)						
    setHighlightColor(new Color(Color.WHITE.getRed, Color.WHITE.getGreen, Color.WHITE.getBlue, 50))
  } else {
    setLineWidth(3)
    setHighlightColor(new Color(Color.BLACK.getRed, Color.BLACK.getGreen, Color.BLACK.getBlue, 50))
  }
  setFollowTerrain(true)                      // so that the lines lay smoothly on the terrain
  setPathType(Polyline.LINEAR)                // so that the lines travel as expected between the
                                                // points on the globe
  setColor(color) 
  setAntiAliasHint(Polyline.ANTIALIAS_NICEST) // this is currently not working as advertised, see
                                        // http://forum.worldwindcentral.com/showthread.php?t=20787

  // Updates the position of the lines endpoint and the annotation based on progress (ranging from
    // 0 to 1) towards the final location.
  def updateLine(progress: Float) = {
    val curPos = Position.interpolate(progress, startPos, endPos) // convenient built in function
                                                // to find intermediate points
    val posArray = new JArrayList[Position]   // an array is needed to set both positions
    posArray.add(startPos)
    posArray.add(curPos)
    setPositions(posArray)
 
    tweetAnno.setPosition(curPos)             // also, update the location of the annotation
  } 
  
  // As additional trees are added to the screen, previous lines progressively fade
    // The alpha parameter is the amount by which this line's alpha should change
  def updateLineOpacity(alpha: Int) = {			
    def calcAlpha(a: Int): Int = 0 max (a - alpha)
                                              // utility function to max difference with zero
    
    val lc = getColor                         // make and set new colors with the new alpha value
    setColor(new Color(lc.getRed, lc.getGreen, lc.getBlue, calcAlpha(lc.getAlpha)))
    val hc = getHighlightColor
    setHighlightColor(new Color(hc.getRed, hc.getGreen, hc.getBlue, calcAlpha(hc.getAlpha)))   
  }
}


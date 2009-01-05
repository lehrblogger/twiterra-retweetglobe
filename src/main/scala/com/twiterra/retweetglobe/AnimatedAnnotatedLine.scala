package com.twiterra.retweetglobe

import gov.nasa.worldwind.render.Polyline
import gov.nasa.worldwind.geom.Position
import java.awt.Color
import java.util.{ArrayList => JArrayList}

class AnimatedAnnotatedLine (val startPos: Position, val endPos: Position, val tweetAnno: TweetAnnotation, val color: Color, val isFollowed:Boolean) extends Polyline
{
  customConfigurations
  
  def updateLine(progress: Float) = {
    val curPos = Position.interpolate(progress, startPos, endPos)
    val posArray = new JArrayList[Position]
    posArray.add(startPos)
    posArray.add(curPos)
    setPositions(posArray)
 
    tweetAnno.setPosition(curPos)
  }
  
  def customConfigurations = {
    setHighlighted(true)
    if (isFollowed) {
      setLineWidth(4)
      setHighlightColor(new Color(Color.WHITE.getRed, Color.WHITE.getGreen, Color.WHITE.getBlue, 50))
    } else {
      setLineWidth(3)
      setHighlightColor(new Color(Color.BLACK.getRed, Color.BLACK.getGreen, Color.BLACK.getBlue, 50))
    }
    setFollowTerrain(true)
    setPathType(Polyline.LINEAR)
    setColor(color)
    setAntiAliasHint(Polyline.ANTIALIAS_FASTEST)
  }
  
  def updateLineOpacity(alpha: Int) = {
    def calcAlpha(a: Int): Int = 0 max (a - alpha)
  
	val lc = getColor
	setColor(new Color(lc.getRed, lc.getGreen, lc.getBlue, calcAlpha(lc.getAlpha)))
	val hc = getHighlightColor
	setHighlightColor(new Color(hc.getRed, hc.getGreen, hc.getBlue, calcAlpha(hc.getAlpha)))   
  }
}


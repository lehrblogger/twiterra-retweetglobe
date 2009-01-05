package com.twiterra.retweetglobe

import gov.nasa.worldwind.render.GlobeAnnotation
import gov.nasa.worldwind.geom.Position
import java.awt.{Color, Font}
/*
class TweetAnnotation (tweetText: String, var position: Position, val color: Color, val followThis: Boolean) 
    extends GlobeAnnotation({
      if ((new Font("Arial Unicode MS", Font.PLAIN, 12)).canDisplayUpTo(tweetText) == -1) {
        println(tweetText + "   " + (new Font("Arial Unicode MS", Font.PLAIN, 12)).canDisplayUpTo(tweetText))
        tweetText 
      } else {
        "font error"
      }}, position, new Font("Arial Unicode MS", Font.PLAIN, 12))
{*/
class TweetAnnotation (tweetText: String, var position: Position, val color: Color, val followThis: Boolean, val isNewTweet: Boolean) 
    extends GlobeAnnotation(tweetText, position, Font.decode("SansSerif"))//, new Font("Arial Unicode MS", Font.PLAIN, 12))
{
  customConfiguratins
  if (followThis) {
    //setAlwaysOnTop(true)
  } else {
    updateAnnotationOpacity(getAttributes.getTextColor.getAlpha / 3)
  }
    
  def customConfiguratins = {
    var annoAttr = getAttributes
    if (isNewTweet) {
      annoAttr.setBorderColor(Color.BLACK)
      annoAttr.setTextColor(Color.BLACK)
      annoAttr.setBackgroundColor(new Color(color.getRed, color.getGreen, color.getBlue, 200))
    } else {
      annoAttr.setBorderColor(color)
      annoAttr.setTextColor(color)
      annoAttr.setBackgroundColor(new Color(Color.BLACK.getRed, Color.BLACK.getGreen, Color.BLACK.getBlue, 200))
    }
    setAttributes(annoAttr)
  }
  
  def updateAnnotationOpacity(alpha: Int) = {
    def calcAlpha(a: Int): Int = 0 max (a - alpha)
  
    val attributes = getAttributes
      
    val bc = attributes.getBorderColor
    attributes.setBorderColor(new Color(bc.getRed, bc.getGreen, bc.getBlue, calcAlpha(bc.getAlpha)))
   
    val bgc = attributes.getBackgroundColor
    attributes.setBackgroundColor(new Color(bgc.getRed, bgc.getGreen, bgc.getBlue, calcAlpha(bgc.getAlpha)))
        
    val tc = attributes.getTextColor
    attributes.setTextColor(new Color(tc.getRed, tc.getGreen, tc.getBlue, calcAlpha(tc.getAlpha)))

    setAttributes(attributes)
  }  
}
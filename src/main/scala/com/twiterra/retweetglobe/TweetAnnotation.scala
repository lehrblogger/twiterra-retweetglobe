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

import gov.nasa.worldwind.render.GlobeAnnotation
import gov.nasa.worldwind.geom.Position
import java.awt.{Color, Font}

// An extension of the build in GlobeAnnotation to support specific appearances
class TweetAnnotation (
  tweetText: String,
  var position: Position,
  val color: Color,
  val followThis: Boolean,
  val isNewTweet: Boolean
) //extends GlobeAnnotation(tweetText, position, Font.decode("SansSerif"))				
  //extends GlobeAnnotation(tweetText, position, Font.decode("Arial-BOLD-12"))
  extends GlobeAnnotation(tweetText, position, new Font("SansSerif", Font.PLAIN, 14)) {
                                                   // All of those should work, they are just
                                                      // different things I tried to get the non-
                                                      // MacRoman characters to display properly
    
  var annoAttr = getAttributes                     // modifies the current attributes, get them first
  if (isNewTweet) {                                // invert the colors for new tweets
    annoAttr.setBorderColor(Color.BLACK)           // modifying the attributes object
    annoAttr.setTextColor(Color.BLACK)
    annoAttr.setBackgroundColor(new Color(color.getRed, color.getGreen, color.getBlue, 200))
  } else {
    annoAttr.setBorderColor(color)
    annoAttr.setTextColor(color)
    annoAttr.setBackgroundColor(new Color(Color.BLACK.getRed, Color.BLACK.getGreen, Color.BLACK.getBlue, 200))
  }
  setAttributes(annoAttr)                          // set the newly modifed attributes object 
  
  if (!followThis) {                               // if this annotation is not being followed
    updateAnnotationOpacity(getAttributes.getTextColor.getAlpha / 3) //, decrease its alpha
  }
    
  // As additional trees are added to the screen, previous annotations progressively fade
    // The alpha parameter is the amount by which this annotations's alpha should change
  def updateAnnotationOpacity(alpha: Int) = {
    def calcAlpha(a: Int): Int = 0 max (a - alpha) // utility function to max difference with zero
  
    val attributes = getAttributes				   // modifies the current attributes, get them first	
      
    val bc = attributes.getBorderColor
    attributes.setBorderColor(new Color(bc.getRed, bc.getGreen, bc.getBlue, calcAlpha(bc.getAlpha)))
   
    val bgc = attributes.getBackgroundColor
    attributes.setBackgroundColor(new Color(bgc.getRed, bgc.getGreen, bgc.getBlue, calcAlpha(bgc.getAlpha)))
        
    val tc = attributes.getTextColor
    attributes.setTextColor(new Color(tc.getRed, tc.getGreen, tc.getBlue, calcAlpha(tc.getAlpha)))

    setAttributes(attributes)                      // set the newly modifed attributes object 
  }  
}
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
package com.twiterra.retweetglobe;

import gov.nasa.worldwind.*;
import gov.nasa.worldwind.awt.WorldWindowGLCanvas;
import javax.swing.*;
import java.awt.*;

// Based on ApplicationTemplate.java with some functionality removed for simplification.
  // This is the main class - the primary change I made here was to make it full screen
public class TwiTerraApp
{
    protected static class AppFrame extends JFrame
    {
		private Dimension canvasSize;
        private TwiTerraAppPanel wwjPanel;

        public AppFrame()
        {	
        	// make full screen
        	this.setUndecorated(true);
        	this.setExtendedState(Frame.MAXIMIZED_BOTH);
        	Toolkit tk = Toolkit.getDefaultToolkit();
    		int xSize = ((int) tk.getScreenSize().getWidth());
    		int ySize = ((int) tk.getScreenSize().getHeight());
    		
            this.initialize(xSize, ySize + 20);
        }

        public AppFrame(int width, int height)
        {
            this.initialize(width, height);
        }

        private void initialize(int width, int height)
        {
            // Create the WorldWindow.
        	this.canvasSize = new Dimension(width, height);
            this.wwjPanel = new TwiTerraAppPanel(canvasSize);
            this.wwjPanel.setPreferredSize(canvasSize);

            // Put the pieces together.
            this.getContentPane().add(wwjPanel, BorderLayout.CENTER);
            this.pack();

            // Center the application on the screen.
            Dimension prefSize = this.getPreferredSize();
            Dimension parentSize;
            java.awt.Point parentLocation = new java.awt.Point(0, 0);
            parentSize = Toolkit.getDefaultToolkit().getScreenSize();
            int x = parentLocation.x + (parentSize.width - prefSize.width) / 2;
            int y = parentLocation.y + (parentSize.height - prefSize.height) / 2;
            this.setLocation(x, y);
            this.setResizable(true);
        }

        public Dimension getCanvasSize()
        {
            return canvasSize;
        }

        public TwiTerraAppPanel getWwjPanel()
        {
            return wwjPanel;
        }

        public WorldWindowGLCanvas getWwd()
        {
            return this.wwjPanel.getWwd();
        }
    }

    static
    {
        if (Configuration.isMacOS())
        {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            System.setProperty("com.apple.mrj.application.apple.menu.about.name", "TwiTerra");
            //System.setProperty("com.apple.mrj.application.growbox.intrudes", "false");
            System.setProperty("apple.awt.brushMetalLook", "true");
        }
    }

    public static void start(String appName, Class<AppFrame> appFrameClass)
    {
        if (Configuration.isMacOS() && appName != null)
        {
            System.setProperty("com.apple.mrj.application.apple.menu.about.name", appName);
        }

        try
        {
            final AppFrame frame = (AppFrame) appFrameClass.newInstance();
            //frame.setTitle(appName);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            
            java.awt.EventQueue.invokeLater(new Runnable()
            {
                public void run()
                {
                    frame.setVisible(true);
                }
            });
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public static void main(String[] args)
    {
        TwiTerraApp.start("TwiTerra - http://twiterra.com", AppFrame.class);
    }
}

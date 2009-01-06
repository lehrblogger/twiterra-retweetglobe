/*
 * TwiTerra
 *   Revealing how people use Twitter to share and re-share ideas, building connections that encircle the world.
 *   http://twiterra.com
 *   http://github.com/lehrblogger/twiterra-retweetglobe/
 * project by Steven Lehrburger
 *   lehrburger (at) gmail (dot) com
 * NYU Interactive Telecommunications Program, Fall 2008
 * Introduction to Computational Media with Dan Shiffman
 */
<?php
  include('/home/memento85/private/dbAccess.php');// database login info kept in a private file
  $retweetsPerSearch = 50;                        // the maximum number of search results per page 
  $null_author = '1';                             // a constant to represent a bad authorname
  $database = 'retweettree';                      // the name of the database
  $englishOnly = '';//'&lang=en';                 // English-only tweets was useful for debugging
                                                  // the strings I was using to find retweets
  $signifiers = array('Retweeting', 'RT', 'R/T', 'RTWT', 'Retweet');
	
	
  dbConnect($database);
  $last_id = 0;                                   // because we only need to search since the last ID stored
  $select_query = "SELECT * FROM tweets ORDER BY tweet_id DESC LIMIT 1";
  $select_result = mysql_query($select_query);
  if ($select_result) {                           // try to get the last ID in the database
    $select_resultArray = mysql_fetch_array($select_result);  
    $last_id =$select_resultArray[0];
  }
  println('last_id = ' . $last_id);               // otherwise it will stay 0, and everything will still work    
  dbClose();
	
  for ($i = 0; $i < count($signifiers); $i += 1) {
    // get the XML search results for each signifier since the last ID, and handle the search results
    $xml = getXMLObjectFromURL('http://search.twitter.com/search.atom?ands=' . $signifiers[$i] . 
							   '&rpp=' . $retweetsPerSearch . 
							   $englishOnly . 
							   '&since_id=' . $last_id);
    handleSearchResults($xml);
  }

// This handles one XML of search results
function handleSearchResults($xml) {
  global $retweetsPerSearch, $database, $null_author;
	
  dbConnect($database);
  
  for ($i = 0; $i < $retweetsPerSearch; $i += 1) {
    // go through the results one by one and try to resolve them
    $tweet_id = resolveTweetChain($xml ->entry[$i], NULL, NULL, true);
  }
 
  dbClose();
}

// This recursive function is long and ocmplex, so follow comments carefully
// The various print statements were useful for debugging, and, since the cron job output is still
// being emailed to me, they might be useful in the future so I am keeping them for now.
// (sorry about their formatting, though - it was to make it easy for me to find things on a page)
function resolveTweetChain($entry, $child_id, $grandchild_id, $is_leaf) {
  if (($entry == NULL) || ($entry->author == NULL)) {
    println('___________NULL ENTRY/AUTHOR');     // if there is no entry and no author
    return false;                                // throw out the tweetand return false
  }
  
                                                 // extract the tweet_id from the string containing it
  $tweet_id = substr($entry->id, strpos($entry->id, ":", strpos($entry->id, ',')) + 1);
                                                 // the user name from the user name/real name string
  $author = strtok(mysql_escape_string($entry->author->name), " ");
  $text = mysql_escape_string($entry->title);    // get the text
  $loc_lat = getLocLat($author);                 // get the location
  $loc_lon = getLocLon($author);
  $time = mysql_escape_string($entry->updated);  // and get the time, properly formatted
	
  println('.................................................................\n\n\n');
  println('#' . $tweet_id . '  ' . $author . ': ' . $text);	
  
  // if a retweet retweets a retweet that was retweeting the first retweet, then it would get stuck in
  // a nasty infinite loop - this prevents it (although larger loops, i suppose, are still possible..)
  if ($tweet_id == $grandchild_id) {
    println('___________INFINITE LOOP AVERTED');
    return false;
  }

  // the same tweet can't be in the database more than once
  if ($is_leaf && isTweetInDB($tweet_id)) {
    println('___________REPEATED LEAF');
    return false;
  }
			
  // if the locations aren't right then we can't use the tweet so throw it out
  if (($loc_lat == '') || ($loc_lon == '')) {
    println('___________INVALID LOCATION');
    return false;
	
  // only retweets with the retweet signifier at the beginning are supported
  // (this makes sure that conversation about retweets don't get in the database)
  } elseif (!isSignifierFirst($text)) {
    // but it's supposed to be a leaf, and it isn't, we should throw it out
    if ($is_leaf) {
      println('___________INVALID RETWEET');
      return false;
    // otherwise, we can insert the tweet in the database, and return it's ID
    // NOTE - I can return a boolean or a value, and php doesn't care...
    } else {
      insertTweet($tweet_id, $author, $text, $loc_lat, $loc_lon, $time, NULL);
      println('-----------ROOT INSERTED');
      // (the parent call will need to know what its parent is, to prevent the loop above (I think))
      return $tweet_id;	
    }

  // otherwise a signifier *is* first, and we need to do some parsing to find the tweet text
  } else {
    $offset_author = strpos($text, '@');                       // get the position of the @
    if ($offset_author) {                                      // if there was an @
      $offset_author += 1;                                     // move past it to the next character
			
      $offset_text = findOffsetText($text, $offset_author);    // find out where the text starts
	  
     // if evreything is ok, we can gtet the author's name
     // which is between those two previously calculated variables
     if (($offset_text > $offset_author) && ($offset_text < strlen($text))) {
        $query_author = substr($text, $offset_author, $offset_text - $offset_author);
																															
      // text is strangely formed, and if we can't parse it we can't use it																										
      } else {
        println('___________WEIRD TEXT FORMATION');
        return false;
      }
			
      $query_text = trim(substr($text, $offset_text + 1));
		
   // there was no @ sign, so we don't know who was being retweeted
    } else {		
      println('___________@AUTHOR NOT IN TWEET');
      return false;
    }
    
    // now that we know the author and text of the retweeted tweet,
    // we need to go and search for the original
    $query_result = searchForTweet($query_author, substr($query_text, 0, 138));
	
	// if we find something
    if ($query_result) {
	  // then we want to recurse back in time, looking for even older original tweets. 
	  // remember this is going in the oppossite direction as the recursion in the display
      $recursive_return = resolveTweetChain($query_result->entry, $tweet_id, $child_id, false);
	 
	  // if the return was successful, and didn't return false, it will evaluate to true
      if ($recursive_return) {
		// and we can insert the tweet into the database, and return the ID to indicate that success
        insertTweet($tweet_id, $author, $text, $loc_lat, $loc_lon, $time, $recursive_return);
        println('-----------BRANCH INSERTED');
        return $tweet_id;
		
	  // otherwise there was a failure at some point up the chain,
	  // and we will have another error message describing it
      } else {
        println('___________(RECURSION FAILED)');
        return false;
      }
	  
	// otherwise we didn't find the tweet searching on Twitter, and we have to return false
    } else {
      println('___________TWEET NOT FOUND');
      return false;
    }		
  }
}

// People use different punctuation to specify their retweets, and this handles some of those cases
function findOffsetText($text, $offset_author) {
  $colon_offset = strpos($text, ':', $offset_author);
  $dash_offset = strpos($text, '-', $offset_author);
  $space_offset = strpos($text, ' ', $offset_author);

  if ($colon_offset < $offset_author) $colon_offset = strlen($text);
  if ($dash_offset < $offset_author) $dash_offset = strlen($text);
  if ($space_offset < $offset_author) $space_offset = strlen($text);
	
  return min($colon_offset, $dash_offset, $space_offset);
}

//Checks to make sure one of the signifier strings is at the beginning of the tweet
function isSignifierFirst($text) {
  global $signifiers;
	
  $tok = strtok($text, " ");
	
  for ($i = 0; $i < count($signifiers); $i += 1) {
    if (($tok == $signifiers[$i]) || ($tok == strtolower($signifiers[$i])))
      return true;
  }
	
  return false;
}

//These two functions use the Twittervision API to get the location of a user
function getLocLat($username) {
  $xml = getXMLObjectFromURL("http://twittervision.com/user/current_status/" . $username . ".xml");
	
  return ($xml->location->latitude);
}
function getLocLon($username) {
  $xml = getXMLObjectFromURL("http://twittervision.com/user/current_status/" . $username . ".xml");
	
  return ($xml->location->longitude);
}

// A utility function for getting an XML object from a URL
function getXMLObjectFromURL($url) {
  $options = array( 'http' => array(
    'user_agent'    => 'student',   // who am i
    'max_redirects' => 10,          // stop after 10 redirects
    'timeout'       => 120          // timeout on response
  ) );
	
  $context = stream_context_create( $options );
  $page    = @file_get_contents( $url, false, $context );

  return (simplexml_load_string($page)); 
}

// Searches for a specific tweet author/text on twitter 
function searchForTweet($author, $text) {
  global $englishOnly;
	
  $xml = getXMLObjectFromURL("http://search.twitter.com/search.atom?ands=" . urlencode($text) . "&from=" . $author . $englishOnly);
	
  return ($xml); 	// TODO TEST THIS FOR FAIL RETURN VALUE
}
	
// Checks to see if a tweet is already in the database, based on its unique ID
function isTweetInDB($tweet_id) {
  $query = "SELECT * FROM tweets WHERE tweet_id=" . $tweet_id;
  $result = mysql_query($query);
  return(mysql_fetch_row($result));
}


// Get's a tweet from the database by author and text, and returns it (or the first, if multiple)
function getTweetID($author, $original) {
  $query = "SELECT tweet_id FROM tweets WHERE author=\"" . $author . "\" AND original=\"" . $original . "\"";
	
  $result = mysql_query($query);
  $resultArray = mysql_fetch_array($result);
	
  return($resultArray[0]);
}

// Inserts a tweet into the database
function insertTweet($tweet_id, $author, $text, $loc_lat, $loc_lon , $time, $parent_id) {
  $time = substr($time, 0, 10) . " " . substr($time, 11, 8); // format the time properly
	
  // if it is a root tweet, we can just insert it	
  if ($parent_id == NULL) {
    $insert_query = "INSERT INTO tweets (tweet_id, author, original, loc_lat, loc_lon, time, num_retweets) values (" . $tweet_id . ", \"" . $author . "\", \"" . $text . "\", " . $loc_lat . ", " . $loc_lon . ", \"" . $time . "\", " . 0 . ")";		
  
  // otherwise we need to grab the parent tweet first and compute the distance between them
  // so that we can insert that distance into the database
  } else {
    $temp_result = mysql_query("SELECT loc_lat, loc_lon FROM tweets WHERE tweet_id=" . $parent_id);
    $temp_resultArray = mysql_fetch_array($temp_result);
    $parent_dist = distance($loc_lat, $loc_lon, $temp_resultArray[0], $temp_resultArray[1], 'm');
	
    $insert_query = "INSERT INTO tweets (tweet_id, author, original, loc_lat, loc_lon, time, num_retweets, parent_id, parent_dist) values (" . $tweet_id . ", \"" . $author . "\", \"" . $text . "\", " . $loc_lat . ", " . $loc_lon . ", \"" . $time . "\", " . 0 . ", " . $parent_id . ", " . $parent_dist . ")";		
  } 

  // if the insert was successful, update the retweet counts all the way up it's parent chain
  $insert_result = mysql_query($insert_query);
  if ($insert_result)	updateRetweetCounts($tweet_id);

  return ($insert_result); //if it fails, it will return false	
}

// Recursively updates the retweet count of a specific tweet
// The retweet count is cumulative for all children and grandchildren and etc
// 
function updateRetweetCounts($tweet_id) {
  // first, get the parent ID of the current tweet
  $select_query = "SELECT parent_id FROM tweets WHERE tweet_id=" . $tweet_id;
  $select_result = mysql_query($select_query);
  $select_resultArray = mysql_fetch_array($select_result);
  $parent_id = $select_resultArray[0];
	
  // the base case - if it is null or the query didn't work, we can't do anything, so return
  if (!$parent_id || ($parent_id == NULL)) {
    return;

  // otherwise, get that parent tweet and increase it's number of retweets by 1
  // and then do the same thing for *that* tweet's parent, again recursing backwards
  } else {
    $select_query = "SELECT num_retweets FROM tweets WHERE tweet_id=" . $parent_id;
    $select_result = mysql_query($select_query);
    $select_resultArray = mysql_fetch_array($select_result);
    $newTotal = $select_resultArray[0] += 1;
	
    $update_query = "UPDATE tweets SET num_retweets=" . $newTotal . " WHERE tweet_id=" . $parent_id;
    mysql_query($update_query);
		
    updateRetweetCounts($parent_id);
  }
}

function println ($string_message) {
  // from http://us2.php.net/manual/en/function.print.php#83241
  // $_SERVER['SERVER_PROTOCOL'] ? print "$string_message<br />" : print "$string_message\n";
  echo($string_message);
}


// I found this code online to compute the distance for me between two lat,lon pairs
/*::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::*/
/*::                                                                         :*/
/*::  this routine calculates the distance between two points (given the     :*/
/*::  latitude/longitude of those points). it is being used to calculate     :*/
/*::  the distance between two zip codes or postal codes using our           :*/
/*::  zipcodeworld(tm) and postalcodeworld(tm) products.                     :*/
/*::                                                                         :*/
/*::  definitions:                                                           :*/
/*::    south latitudes are negative, east longitudes are positive           :*/
/*::                                                                         :*/
/*::  passed to function:                                                    :*/
/*::    lat1, lon1 = latitude and longitude of point 1 (in decimal degrees)  :*/
/*::    lat2, lon2 = latitude and longitude of point 2 (in decimal degrees)  :*/
/*::    unit = the unit you desire for results                               :*/
/*::           where: 'm' is statute miles                                   :*/
/*::                  'k' is kilometers (default)                            :*/
/*::                  'n' is nautical miles                                  :*/
/*::  united states zip code/ canadian postal code databases with latitude & :*/
/*::  longitude are available at http://www.zipcodeworld.com                 :*/
/*::                                                                         :*/
/*::  For enquiries, please contact sales@zipcodeworld.com                   :*/
/*::                                                                         :*/
/*::  official web site: http://www.zipcodeworld.com                         :*/
/*::                                                                         :*/
/*::  hexa software development center © all rights reserved 2004            :*/
/*::                                                                         :*/
/*::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::*/
function distance($lat1, $lon1, $lat2, $lon2, $unit) { 
  if (($lat1 == $lat2) && ($lon1 == $lon2)) return 0;

  $theta = $lon1 - $lon2; 
  $dist = sin(deg2rad($lat1)) * sin(deg2rad($lat2)) +  cos(deg2rad($lat1)) * cos(deg2rad($lat2)) * cos(deg2rad($theta)); 
  $dist = acos($dist); 
  $dist = rad2deg($dist); 
  $miles = $dist * 60 * 1.1515;
  $unit = strtoupper($unit);

  if ($unit == "K") {
    return ($miles * 1.609344); 
  } else if ($unit == "N") {
    return ($miles * 0.8684);
  } else {
    return $miles;
  }
}

?>


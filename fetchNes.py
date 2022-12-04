#!/usr/bin/python

#
#  This python script illustrates fetching information from a CGI program
#  that typically gets its data via an HTML form using a POST method.
#
#  Copyright (c) 2018, Carnegie Mellon University.  All Rights Reserved.
#

import requests

#  ===> FILL IN YOUR PARAMETERS <===

userId = 'yiyunh@andrew.cmu.edu'
password = '97JjksBg'
# fileIn = './HW5-Exp-1.1b.teIn'
# fileIn = './QryEval/QryOut/HW5-Exp-1.1b.teIn'
fileIn = './QryEval/HW5-Exp-1.1b.teIn'

#  Form parameters - these must match form parameters in the web page

url = 'http://boston.lti.cs.cmu.edu/classes/11-642/HW/HTS/nes.cgi'
values = { 'qrel' : 'cw09a.diversity.1-200.qrel.indexed',	# cgi parameter
	   'hwid' : 'HW5'					# cgi parameter
           }

#  Make the request

files = {'infile' : (fileIn, open(fileIn, 'rb')) }		# cgi parameter
result = requests.post (url, data=values, files=files, auth=(userId, password))

#  Replace the <br /> with \n for clarity

print (result.text.replace ('<br />', '\n'))
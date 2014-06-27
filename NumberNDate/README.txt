README

Additional JARs:
In order to build and run this project, you need some additional JARs that for the moment I have
not added to the repository. I _think_ I should be able to add them, but I need to figure out
the licensing situation first. Until that time, here are the JARs that you should need to add:
	<classpathentry exported="true" kind="lib" path=".../gdata/java/lib/gdata-core-1.0.jar"/>
	<classpathentry exported="true" kind="lib" path=".../gdata/java/deps/guava-11.0.2.jar"/>
	<classpathentry exported="true" kind="lib" path=".../gdata/java/lib/gdata-spreadsheet-3.0.jar"/>
	<classpathentry exported="true" kind="lib" path=".../gdata/java/lib/gdata-spreadsheet-meta-3.0.jar"/>
	<classpathentry exported="true" kind="lib" path=".../gdata/java/lib/gdata-client-1.0.jar"/>
	<classpathentry exported="true" kind="lib" path=".../gdata/java/deps/jsr305.jar"/>

These should all basically be Google-able. (In fact, some of them I found on Google.) Please
let me know if I am missing any.

June 24, 2014
Starting a new project, based on Megabudget that I think should be much easier and still
useful. You select a spreadsheet. You type in a number you hit a button, and it adds a
new row to a spreadsheet with both the number and the date. Helps you track stuff like
your weight over time, but in your own spreadsheets.
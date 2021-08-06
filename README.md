#GITHUBRANK CHALLANGE BY @lucasrpb

For the implementation of the task it was used Akka Streams because it is a functional and composable library who allows 
us to express pretty complex computations using basic abstract components: Source (the input), Flow (the computation) and
Sink (the output). The library comes with very useful default components. Among those there is a one called throttle, 
which was used in the implementation to overcome the GitHub API request rate limit :)

The HTTP Etag cache specification supported by GitHub it was also implemented to help reduce the number of api request 
counts. For the content storage MapDB, a popular Java collection implementation, was used as persistent storage. In memory
cache layer was also implemented to speed up things a little bit! 

**Be aware that for big organizations the time to get a response can be huge! This is due to the throttling!**

To run the server you must first set a system variable called GH_TOKEN with the value as your github developer token! (this 
is necessary because without it the request limit by hour is only 60!). On Windows it can be set on environment variables 
section (unfortunately I was not able to make it work on Windows Terminal :/). On Linux and Mac you can do in the terminal: 

**export GH_TOKEN="my_token_goes_here"**

With token set, you will be able to run the application doing: sbt run 

The server runs at port 8080. To check the rank of contributions for a certain repo you can use curl as follows (this is 
an example):

**curl -X GET localhost:8080/org/spray/contributors**

Finally, for testing the solution: 

**sbt test**

**One more thing: to stop the server, press any key :P**

That's all, folks!
Remote Execution Gradle Plugin
==================

#### Table of Contents  
[What is it](#What is it)  

What is it
----------

This is a first shot a building a Gradle plugin which allows executing tests remotely on Jenkins. A first version was created during the Jenkins Hackathon in Munich 2013. For the design doc see this Gradle [Pull Request](https://github.com/gradle/gradle/pull/163).

To make it run you need a Jenkins instance running with the unreleased version of the remote-terminal-access-plugin running at http://localhost:8080/jenkins.
Then just use
```
./gradlew clean test
```

and see the magic happen.

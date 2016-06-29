This is a test for the WebID / SoLiD proxy service:

https://github.com/jolocom/webid-proxy

**Warning:** When the test is run, all users in both the proxy and the SoLiD server will be deleted!

### How to run

Just run

    mvn clean install exec:java \
    	-Dwebidproxy.test.alluserswillbedeleted \
    	-Dwebidproxy.test.pathToSolid=/opt/solid \
    	-Dwebidproxy.test.pathToProxy=/opt/webid-proxy \
    	-Dwebidproxy.test.proxyUrl=http://localhost:8111 \
    	-Dwebidproxy.test.webIdHost=mywebid.com:8443

To build and run the WebID proxy test.

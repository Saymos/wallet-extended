## Original Assignment Text from Cubeia

**Java Wallet Coding Challenge**  
**The task**  
Implement a basic bookkeeping (accounting) application that keeps track of funds. Also called a “wallet” in online gaming terminology.  
**Deliverables**  
Link to GitHub repository (or similar) or a zip-archive. Instructions on how to build and run. Basic API documentation.  
**Description & Requirements**  
Implement a basic bookkeeping service that handles monetary transactions and keeps track of account balances. This is similar to a normal bank account.  
The service should have at least the following API methods:  
1. get balance - return the balance for an account  
2. transfer - transfer funds to or from an account  
3. list transactions - list transaction entries for an account  
4. create account (optional) - create an account, this can be done implicitly when creating transactions or explicitly  
The implementation should be a HTTP server exposing the API using REST. No UI is required. We will be using curl or POSTman to test the API.  
**Implementation**  
You can choose the effort put into the test yourself. We estimate it to take around 4 hours depending on experience and scope. The code needs to be thread safe and should work in an environment where we might be running a cluster of wallet servers. Implementation shortcuts are acceptable as long as they are documented and the proper solution is outlined.  
Keep this in mind:  
- Correctness is important - it should never be possible to get an incorrect balance of an account  
- The code should be readable and easy to follow  
- Thread safety and concurrency will be considered as an important aspect.  
**Technology**  
- Java 17+  
- REST framework, preferably Spring Boot  
- Avoid using Project Lombok  
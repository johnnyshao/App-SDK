# Witte SDK Android Sample v1.6.0.3
This Android App demos the usage of the Tapkey SDK and the basic communication with the Witte Digital API Management backend. 

## Preparation
### SDK Key, API Subscription Key and Ids
Please assign your keys and ids to the constants in the App class (App.java)
``` java
     // TODO: add your customer Id here
    public final static int MyCustomerId = 0; 

     // TODO: add your user Id here
    public final static int MyUserId = 0; 

     // TODO: add your SDK key here
    public final static String MySdkKey = ""; 

     // TODO: add your API subscription key here
    public final static String MySubKey = "";

     // TODO: add your service Id here (e.g. Id for Witte Wave box)
    public final static int MyServiceId = 0; 

     // TODO: add the Id of your Witte Wave box here e.g. 'C108F094'
    public final static String MyPhysicalLockId = ""; 
```

### Tapkey SDK
In order to build and run the example you need add the Tapkey SDK v1.6.0.3 to your local Libs directory right here.
```
|-- Witte SDK Integration
    |-- Android
        |-- Libs
            |-- net
                |-- tpky
                    |-- Tapkey.Common
                    |-- Tapkey.Desfire
                    |-- Tapkey.MobileLib
                    |-- Tapkey.Nfc
                    |-- Tapkey.SharedLib 
        |-- Tapkey.SdkSample
            |-- .gradle
            |-- .idea
            |-- app
            |-- build
            |-- gradle
```
Please write to contact@witte.digital to order your copy of the Tapkey SDK.


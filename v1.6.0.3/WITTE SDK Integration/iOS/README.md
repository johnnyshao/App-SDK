# Witte SDK Android Sample v1.6.0.3
This iOS App demos the usage of the Tapkey SDK and the basic communication with the Witte Digital API Management backend. 

## Preparation
### SDK Key, API Subscription Key and Ids
Please assign your keys and ids to the constants in the App class (App.java)
``` Swift
    // TODO: add your customer Id here
    let myCustomerId = 0 
    
    // TODO: add your user Id here
    let myUserId = 0 
    
    // TODO: add your SDK key here
    let mySdkKey = "" 
    
    // TODO: add your API subscription key here
    let mySubKey = "" 
    
    // TODO: add your service Id here (e.g. Id for Witte Wave box)
    let myServiceId = 0 
```
### Tapkey SDK
In order to build and run the example you need add the Tapkey SDK v1.6.0.3 to your local Libs directory right here.
```
|-- Witte SDK Integration
    |-- iOS
        |-- Libs
            |-- Headers
            |-- TapkeyMobileLib.framework
            |-- TapkeyMobileLibSource
        |-- Tapkey.SdkSample
            |-- CommonCrypto
            |-- Pods
            |-- SdkSample
            |-- SdkSample.xcodeproj
            |-- SdkSample.xcworkspace
```
Please write to contact@witte.digital to order your copy of the Tapkey SDK.

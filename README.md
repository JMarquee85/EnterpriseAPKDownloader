# Marriott Large APK Downloader (MLAD)
## For Corporate Managed Shared Androids 

### Description

This application is designed to host, download, and install applications for Shared Android devices
that are too large to be hosted either by the Managed Google Play Store or through Intune as LOB
apps or otherwise. 

This application may also be used to "force" install applications that are needed. 

The application is a custom built APK that is to be deployed to Marriott Shared Android devices that
need additional apps downloaded and installed to them. 

Current use cases include SILK Android devices that need FreedomPay and Simphony. 

An additional use case is a potential method to deploy the custom Marriott Salesforce APK, which 
is currently too large to deploy through standard means. 

MLAD is designed currently to be deployed on fully managed, corporate-owned devices. The application
aims to run as a background service and perform silent installs.

### Process

#### Android Studio

First, an MLAD APK needs to be exported from Android Studio.

##### Permissions

Permissions requested on the device from the MLAD APK are: 

`INTERNET`
Required to reach out to S3

`READ_PHONE_STATE`
Required to get the Android ID for logging purposes

`REQUEST_INSTALL_PACKAGES`
Required to request downloaded APK packages get installed

#### Intune

That APK needs to then be hosted in Intune and deployed to the Shared Android devices that need to
utilize its function. 

This should deploy the application to the devices. 

Additionally, Managed App Configurations need to be deployed to the devices that match what the 
Marriott Large APK Downloader is looking for to ensure that the intended applications are installed
on the device. 

Currently, MLAD (the Marriott Large APK Downloader) expects an app configuration that contains 
something like this:

```
{
"deviceGroups": "SILKSharedAppDeploy"
}
```

There are two options for deviceGroups at this time: 
`SILKSharedAppDeploy`
`GXPAppDeploy`

The value sent to the devices via Intune should be a key value pair, matching the above example, and
can contain one or both of the deviceGroups as a comma separated value. 

For instance, if both applications needed to be deployed to a specific group, the Intune Managed
App Configuration would look like the following:

```
{
"deviceGroups": "SILKSharedAppDeploy,GXPAppDeploy"
}
```

If MLAD does not see one or both of these items, the applications will not be installed. 

If the Intune Managed App Configuration that contains the `deviceGroups` key-value pair is removed,
the related applications should also be removed from the device. 


#### AWS S3 Bucket

An S3 bucket should be configured that contains: 

- The APK files to be installed
- A `manifest.json` file that determines actions to be taken on devices that are checking in
- A `logs` directory that will be created if not exists when the app runs for the first time

An IAM policy granting access to read and write to that bucket should be configured. 

An IAM user with access to that IAM policy should be created and access keys and secret keys created. 

Currently the AWS Access Key and AWS Secret Key are expected in a local `local.properties` file as such:

awsAccessKey={{the_access_key}}
awsSecretKey={{the_secret_key}}

This should be addressed differently soon. AWS Cognito to get short-lived credentials at runtime
is an option that I might look into as development continues. 

##### manifest.json

The `manifest.json` file should reflect the following format: 

```
{
  "apps": [
    {
      "appName": "com.example.FreedomPay",
      "Action": "INSTALL",
      "Version": 2.17,
      "targetGroups": ["SILKSharedAppDeploy"]
    },
    {
      "appName": "com.example.Simphony",
      "Action": "INSTALL",
      "Version": 1.5,
      "targetGroups": ["SILKSharedAppDeploy"]
    },
    {
      "appName": "com.example.MarriottSalesforce",
      "Action": "INSTALL",
      "Version": 1.0,
      "targetGroups": ["GXPAppDeploy"]
    }
  ]
}
```

##### logs

Logs for the device's operations are sent to the S3 bucket and saved in a format that includes the 
Android ID. 

The Android ID is not natively stored in Intune. If device level logs are needed, they should be
accessible to the device user currently by a low priority notification that will appear on the device
notifying the user that debug information is available. 

This should be triggered if anything that is deemed an error occurs on the device. 

If the user presses the notification in the system tray, they should see the Android ID, which they
could then report to a UEM Mobile team member for investigation. A log file should be stored in the 
S3 bucket that contains that Android ID that should be able to be accessed and reviewed. 

### MLAD Function

Periodically, MLAD will check the S3 bucket and download the `manifest.json` file. 

MLAD will then parse the `manifest.json` and look for the pushed Intune Managed App Configuration 
strings to determine what action should be taken. 

MLAD will check if the device contains the key-value pair from the Intune Managed App Configuration that determines
they should have access to an APK that is stored in the S3 bucket.

If so, the device is then checked to see if an application is installed. 

If not, and the device is scoped to receive the application, installation is attempted. 

If so, the version of the installed application is checked against the version number listed in the
`manifest.json`. If the version number is greater, the application is uninstalled and reinstalled 
with the newer version of the application. 

The `manifest.json` may also have `"Action":"REMOVE"` set for any given application. If so, ANY DEVICE
THAT CHECKS IN TO THE `manifest.json` WILL REMOVE THE APPLICATION. 

This function should ONLY be used in the instance where all devices that are using this deployment
method need a specific application removed. This might occur in the instance where a third party
app vendor is able to get their application down to a size which is deployable in Intune. If so, 
that would be the most desirable deployment method. 


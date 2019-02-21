package sample;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.androidmanagement.v1.AndroidManagement;
import com.google.api.services.androidmanagement.v1.model.ApplicationPolicy;
import com.google.api.services.androidmanagement.v1.model.Command;
import com.google.api.services.androidmanagement.v1.model.Device;
import com.google.api.services.androidmanagement.v1.model.EnrollmentToken;
import com.google.api.services.androidmanagement.v1.model.Enterprise;
import com.google.api.services.androidmanagement.v1.model.ListDevicesResponse;
import com.google.api.services.androidmanagement.v1.model.PersistentPreferredActivity;
import com.google.api.services.androidmanagement.v1.model.Policy;
import com.google.api.services.androidmanagement.v1.model.SignupUrl;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SampleApp {
  /** The id of the Google Cloud Platform project. */
  private static final String PROJECT_ID = "YOUR_PROJECT_ID";

  /** The JSON credential file for the service account. */
  private static final String SERVICE_ACCOUNT_CREDENTIAL_FILE =
      "/PATH/TO/YOUR_FILE";

  /** The id of the policy for the COSU device. */
  private static final String POLICY_ID = "samplePolicy";

  /** The package name of the COSU app. */
  private static final String COSU_APP_PACKAGE_NAME =
      "com.google.android.apps.youtube.gaming";

  /** The OAuth scope for the Android Management API. */
  private static final String OAUTH_SCOPE =
      "https://www.googleapis.com/auth/androidmanagement";

  /** The name of this app. */
  private static final String APP_NAME = "Android Management API sample app";

  /** The Android Management API client. */
  private final AndroidManagement androidManagementClient;

  public static void main(String[] args)
      throws IOException, GeneralSecurityException {
    new SampleApp(getAndroidManagementClient()).run();
  }

  public SampleApp(AndroidManagement androidManagementClient) {
    this.androidManagementClient = androidManagementClient;
  }

  /** Runs the app. */
  public void run() throws IOException {
    // Create an enterprise. If you've already created an enterprise, the
    // createEnterprise call can be commented out and replaced with your
    // enterprise name.
    String enterpriseName = createEnterprise();
    System.out.println("Enterprise created with name: " + enterpriseName);

    // Set the policy to be used by the device.
    setPolicy(enterpriseName, POLICY_ID, getCosuPolicy());

    // Create an enrollment token to enroll the device.
    String token = createEnrollmentToken(enterpriseName, POLICY_ID);
    System.out.println("Enrollment token (to be typed on device): " + token);

    // List some of the devices for the enterprise. There will be no devices for
    // a newly created enterprise, but you can run the app again with an
    // existing enterprise after enrolling a device.
    List<Device> devices = listDevices(enterpriseName);
    for (Device device : devices) {
      System.out.println("Found device with name: " + device.getName());
    }

    // If there are any devices, reboot one.
    if (devices.isEmpty()) {
      System.out.println("No devices found.");
    } else {
      rebootDevice(devices.get(0));
    }
  }

  /** Builds an Android Management API client. */
  private static AndroidManagement getAndroidManagementClient()
      throws IOException, GeneralSecurityException {
    try (FileInputStream input =
        new FileInputStream(SERVICE_ACCOUNT_CREDENTIAL_FILE)) {
      GoogleCredential credential =
          GoogleCredential.fromStream(input)
              .createScoped(Collections.singleton(OAUTH_SCOPE));
      return new AndroidManagement.Builder(
          GoogleNetHttpTransport.newTrustedTransport(),
          JacksonFactory.getDefaultInstance(),
          credential)
              .setApplicationName(APP_NAME)
              .build();
    }
  }

  /** Creates a new enterprise. Returns the enterprise name. */
  private String createEnterprise() throws IOException {
    // Initiate signup process.
    System.out.println("Creating signup URL...");
    SignupUrl signupUrl =
        androidManagementClient
            .signupUrls()
            .create()
            .setProjectId(PROJECT_ID)
            .setCallbackUrl("https://localhost:9999")
            .execute();
    System.out.print(
        "To sign up for a new enterprise, open this URL in your browser: ");
    System.out.println(signupUrl.getUrl());
    System.out.println(
        "After signup, you will see an error page in the browser.");
    System.out.print(
        "Paste the enterpriseToken value from the error page URL here: ");
    String enterpriseToken =
        new BufferedReader(new InputStreamReader(System.in)).readLine();

    // Create the enterprise.
    System.out.println("Creating enterprise...");
    return androidManagementClient
        .enterprises()
        .create(new Enterprise())
        .setProjectId(PROJECT_ID)
        .setSignupUrlName(signupUrl.getName())
        .setEnterpriseToken(enterpriseToken)
        .execute()
        .getName();
  }

  /** Gets a Policy for a COSU device. */
  private Policy getCosuPolicy() {
    List<String> categories = new ArrayList<>();
    categories.add("android.intent.category.HOME");
    categories.add("android.intent.category.DEFAULT");

    return new Policy()
        .setApplications(
            Collections.singletonList(
                new ApplicationPolicy()
                    .setPackageName(COSU_APP_PACKAGE_NAME)
                    .setInstallType("FORCE_INSTALLED")
                    .setDefaultPermissionPolicy("GRANT")
                    .setLockTaskAllowed(true)))
        .setPersistentPreferredActivities(
            Collections.singletonList(
                new PersistentPreferredActivity()
                    .setReceiverActivity(COSU_APP_PACKAGE_NAME)
                    .setActions(
                        Collections.singletonList("android.intent.action.MAIN"))
                    .setCategories(categories)))
        .setKeyguardDisabled(true)
        .setStatusBarDisabled(true);
  }

  /** Sets the policy of the given id to the given value. */
  private void setPolicy(String enterpriseName, String policyId, Policy policy)
      throws IOException {
    System.out.println("Setting policy...");
    String name = enterpriseName + "/policies/" + policyId;
    androidManagementClient
        .enterprises()
        .policies()
        .patch(name, policy)
        .execute();
  }

  /** Creates an enrollment token. */
  private String createEnrollmentToken(String enterpriseName, String policyId)
      throws IOException {
    System.out.println("Creating enrollment token...");
    EnrollmentToken token =
        new EnrollmentToken().setPolicyName(policyId).setDuration("86400s");
    return androidManagementClient
        .enterprises()
        .enrollmentTokens()
        .create(enterpriseName, token)
        .execute()
        .getValue();
  }

  /** Lists the first page of devices for an enterprise. */
  private List<Device> listDevices(String enterpriseName) throws IOException {
    System.out.println("Listing devices...");
    ListDevicesResponse response =
        androidManagementClient
            .enterprises()
            .devices()
            .list(enterpriseName)
            .execute();
    return response.getDevices() == null
        ? new ArrayList<>() : response.getDevices();
  }

  /** Reboots a device. Note that reboot only works on Android N+. */
  private void rebootDevice(Device device) throws IOException {
    System.out.println(
        "Sending reboot command to " + device.getName() + "...");
    Command command = new Command().setType("REBOOT");
    androidManagementClient
        .enterprises()
        .devices()
        .issueCommand(device.getName(), command)
        .execute();
  }
}
// automatically generated by the FlatBuffers compiler, do not modify

package flatbuffers.events;

public final class EventType {
  private EventType() { }
  public static final byte LOGIN = 0;
  public static final byte LOGIN_ERROR = 1;
  public static final byte REGISTER = 2;
  public static final byte REGISTER_ERROR = 3;
  public static final byte LOGOUT = 4;
  public static final byte LOGOUT_ERROR = 5;
  public static final byte CODE_TO_TOKEN = 6;
  public static final byte CODE_TO_TOKEN_ERROR = 7;
  public static final byte CLIENT_LOGIN = 8;
  public static final byte CLIENT_LOGIN_ERROR = 9;
  public static final byte REFRESH_TOKEN = 10;
  public static final byte REFRESH_TOKEN_ERROR = 11;
  public static final byte VALIDATE_ACCESS_TOKEN = 12;
  public static final byte VALIDATE_ACCESS_TOKEN_ERROR = 13;
  public static final byte INTROSPECT_TOKEN = 14;
  public static final byte INTROSPECT_TOKEN_ERROR = 15;
  public static final byte FEDERATED_IDENTITY_LINK = 16;
  public static final byte FEDERATED_IDENTITY_LINK_ERROR = 17;
  public static final byte REMOVE_FEDERATED_IDENTITY = 18;
  public static final byte REMOVE_FEDERATED_IDENTITY_ERROR = 19;
  public static final byte UPDATE_EMAIL = 20;
  public static final byte UPDATE_EMAIL_ERROR = 21;
  public static final byte UPDATE_PROFILE = 22;
  public static final byte UPDATE_PROFILE_ERROR = 23;
  public static final byte UPDATE_PASSWORD = 24;
  public static final byte UPDATE_PASSWORD_ERROR = 25;
  public static final byte UPDATE_TOTP = 26;
  public static final byte UPDATE_TOTP_ERROR = 27;
  public static final byte VERIFY_EMAIL = 28;
  public static final byte VERIFY_EMAIL_ERROR = 29;
  public static final byte REMOVE_TOTP = 30;
  public static final byte REMOVE_TOTP_ERROR = 31;
  public static final byte REVOKE_GRANT = 32;
  public static final byte REVOKE_GRANT_ERROR = 33;
  public static final byte SEND_VERIFY_EMAIL = 34;
  public static final byte SEND_VERIFY_EMAIL_ERROR = 35;
  public static final byte SEND_RESET_PASSWORD = 36;
  public static final byte SEND_RESET_PASSWORD_ERROR = 37;
  public static final byte SEND_IDENTITY_PROVIDER_LINK = 38;
  public static final byte SEND_IDENTITY_PROVIDER_LINK_ERROR = 39;
  public static final byte RESET_PASSWORD = 40;
  public static final byte RESET_PASSWORD_ERROR = 41;
  public static final byte RESTART_AUTHENTICATION = 42;
  public static final byte RESTART_AUTHENTICATION_ERROR = 43;
  public static final byte INVALID_SIGNATURE = 44;
  public static final byte INVALID_SIGNATURE_ERROR = 45;
  public static final byte REGISTER_NODE = 46;
  public static final byte REGISTER_NODE_ERROR = 47;
  public static final byte UNREGISTER_NODE = 48;
  public static final byte UNREGISTER_NODE_ERROR = 49;
  public static final byte USER_INFO_REQUEST = 50;
  public static final byte USER_INFO_REQUEST_ERROR = 51;
  public static final byte IDENTITY_PROVIDER_LINK_ACCOUNT = 52;
  public static final byte IDENTITY_PROVIDER_LINK_ACCOUNT_ERROR = 53;
  public static final byte IDENTITY_PROVIDER_LOGIN = 54;
  public static final byte IDENTITY_PROVIDER_LOGIN_ERROR = 55;
  public static final byte IDENTITY_PROVIDER_FIRST_LOGIN = 56;
  public static final byte IDENTITY_PROVIDER_FIRST_LOGIN_ERROR = 57;
  public static final byte IDENTITY_PROVIDER_POST_LOGIN = 58;
  public static final byte IDENTITY_PROVIDER_POST_LOGIN_ERROR = 59;
  public static final byte IDENTITY_PROVIDER_RESPONSE = 60;
  public static final byte IDENTITY_PROVIDER_RESPONSE_ERROR = 61;
  public static final byte IDENTITY_PROVIDER_RETRIEVE_TOKEN = 62;
  public static final byte IDENTITY_PROVIDER_RETRIEVE_TOKEN_ERROR = 63;
  public static final byte IMPERSONATE = 64;
  public static final byte IMPERSONATE_ERROR = 65;
  public static final byte CUSTOM_REQUIRED_ACTION = 66;
  public static final byte CUSTOM_REQUIRED_ACTION_ERROR = 67;
  public static final byte EXECUTE_ACTIONS = 68;
  public static final byte EXECUTE_ACTIONS_ERROR = 69;
  public static final byte EXECUTE_ACTION_TOKEN = 70;
  public static final byte EXECUTE_ACTION_TOKEN_ERROR = 71;
  public static final byte CLIENT_INFO = 72;
  public static final byte CLIENT_INFO_ERROR = 73;
  public static final byte CLIENT_REGISTER = 74;
  public static final byte CLIENT_REGISTER_ERROR = 75;
  public static final byte CLIENT_UPDATE = 76;
  public static final byte CLIENT_UPDATE_ERROR = 77;
  public static final byte CLIENT_DELETE = 78;
  public static final byte CLIENT_DELETE_ERROR = 79;
  public static final byte CLIENT_INITIATED_ACCOUNT_LINKING = 80;
  public static final byte CLIENT_INITIATED_ACCOUNT_LINKING_ERROR = 81;
  public static final byte UNKNOWN = 82;

  public static final String[] names = { "LOGIN", "LOGIN_ERROR", "REGISTER", "REGISTER_ERROR", "LOGOUT", "LOGOUT_ERROR", "CODE_TO_TOKEN", "CODE_TO_TOKEN_ERROR", "CLIENT_LOGIN", "CLIENT_LOGIN_ERROR", "REFRESH_TOKEN", "REFRESH_TOKEN_ERROR", "VALIDATE_ACCESS_TOKEN", "VALIDATE_ACCESS_TOKEN_ERROR", "INTROSPECT_TOKEN", "INTROSPECT_TOKEN_ERROR", "FEDERATED_IDENTITY_LINK", "FEDERATED_IDENTITY_LINK_ERROR", "REMOVE_FEDERATED_IDENTITY", "REMOVE_FEDERATED_IDENTITY_ERROR", "UPDATE_EMAIL", "UPDATE_EMAIL_ERROR", "UPDATE_PROFILE", "UPDATE_PROFILE_ERROR", "UPDATE_PASSWORD", "UPDATE_PASSWORD_ERROR", "UPDATE_TOTP", "UPDATE_TOTP_ERROR", "VERIFY_EMAIL", "VERIFY_EMAIL_ERROR", "REMOVE_TOTP", "REMOVE_TOTP_ERROR", "REVOKE_GRANT", "REVOKE_GRANT_ERROR", "SEND_VERIFY_EMAIL", "SEND_VERIFY_EMAIL_ERROR", "SEND_RESET_PASSWORD", "SEND_RESET_PASSWORD_ERROR", "SEND_IDENTITY_PROVIDER_LINK", "SEND_IDENTITY_PROVIDER_LINK_ERROR", "RESET_PASSWORD", "RESET_PASSWORD_ERROR", "RESTART_AUTHENTICATION", "RESTART_AUTHENTICATION_ERROR", "INVALID_SIGNATURE", "INVALID_SIGNATURE_ERROR", "REGISTER_NODE", "REGISTER_NODE_ERROR", "UNREGISTER_NODE", "UNREGISTER_NODE_ERROR", "USER_INFO_REQUEST", "USER_INFO_REQUEST_ERROR", "IDENTITY_PROVIDER_LINK_ACCOUNT", "IDENTITY_PROVIDER_LINK_ACCOUNT_ERROR", "IDENTITY_PROVIDER_LOGIN", "IDENTITY_PROVIDER_LOGIN_ERROR", "IDENTITY_PROVIDER_FIRST_LOGIN", "IDENTITY_PROVIDER_FIRST_LOGIN_ERROR", "IDENTITY_PROVIDER_POST_LOGIN", "IDENTITY_PROVIDER_POST_LOGIN_ERROR", "IDENTITY_PROVIDER_RESPONSE", "IDENTITY_PROVIDER_RESPONSE_ERROR", "IDENTITY_PROVIDER_RETRIEVE_TOKEN", "IDENTITY_PROVIDER_RETRIEVE_TOKEN_ERROR", "IMPERSONATE", "IMPERSONATE_ERROR", "CUSTOM_REQUIRED_ACTION", "CUSTOM_REQUIRED_ACTION_ERROR", "EXECUTE_ACTIONS", "EXECUTE_ACTIONS_ERROR", "EXECUTE_ACTION_TOKEN", "EXECUTE_ACTION_TOKEN_ERROR", "CLIENT_INFO", "CLIENT_INFO_ERROR", "CLIENT_REGISTER", "CLIENT_REGISTER_ERROR", "CLIENT_UPDATE", "CLIENT_UPDATE_ERROR", "CLIENT_DELETE", "CLIENT_DELETE_ERROR", "CLIENT_INITIATED_ACCOUNT_LINKING", "CLIENT_INITIATED_ACCOUNT_LINKING_ERROR", "UNKNOWN", };

  public static String name(int e) { return names[e]; }
}


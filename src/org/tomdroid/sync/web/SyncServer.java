package org.tomdroid.sync.web;

import java.net.UnknownHostException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tomdroid.util.Preferences;

import android.util.Log;

public class SyncServer {

	final String	TAG				= "UserInfo";
	final String	userReference	= Preferences.getString(Preferences.Key.SYNC_SERVER_USER_API);
	private String	notesApiReference;
	private String	userName;
	private String	firstName;
	private String	lastName;
	private Long	syncVersionOnServer;
	private Long	currentSyncGuid;
	private OAuthConnection  authConnection;

	public SyncServer() throws UnknownHostException, JSONException {
		authConnection = getAuthConnection();

		String rawResponse = authConnection.get(userReference);
		JSONObject response = new JSONObject(rawResponse);
		Log.v(TAG, "userRef response: " + response.toString());
		notesApiReference = response.getJSONObject("notes-ref").getString("api-ref");
		
		userName = response.getString("user-name");
		firstName = response.getString("first-name");
		lastName = response.getString("last-name");
		
		syncVersionOnServer = response.getLong("latest-sync-revision");
		currentSyncGuid = response.getLong("current-sync-guid");
	}

	private String getNotesUri() {
		return notesApiReference;
	}
	
	public boolean isInSync(){
		long syncVersionOnClient = (Long) Preferences.getLong(Preferences.Key.LATEST_SYNC_REVISION);
		return syncVersionOnClient == syncVersionOnServer;	
	}
	
	public JSONArray getNotes() throws UnknownHostException, JSONException {
		JSONObject response = new JSONObject(authConnection.get(getNotesUri() + "?include_notes=true"));
		return response.getJSONArray("notes");
	}
	
	public void onSyncDone() {
		Preferences.putLong(Preferences.Key.LATEST_SYNC_REVISION, syncVersionOnServer);
	}

	public static OAuthConnection getAuthConnection() {
		
		OAuthConnection auth = new OAuthConnection();
		
		auth.accessToken = Preferences.getString(Preferences.Key.ACCESS_TOKEN);
		auth.accessTokenSecret = Preferences.getString(Preferences.Key.ACCESS_TOKEN_SECRET);
		auth.requestToken = Preferences.getString(Preferences.Key.REQUEST_TOKEN);
		auth.requestTokenSecret = Preferences.getString(Preferences.Key.REQUEST_TOKEN_SECRET);
		auth.oauth10a = Preferences.getBoolean(Preferences.Key.OAUTH_10A);
		auth.authorizeUrl = Preferences.getString(Preferences.Key.AUTHORIZE_URL);
		auth.accessTokenUrl = Preferences.getString(Preferences.Key.ACCESS_TOKEN_URL);
		auth.requestTokenUrl = Preferences.getString(Preferences.Key.REQUEST_TOKEN_URL);
		auth.rootApi = Preferences.getString(Preferences.Key.SYNC_SERVER_ROOT_API);
		auth.userApi = Preferences.getString(Preferences.Key.SYNC_SERVER_USER_API);
		
		return auth;
	}

}
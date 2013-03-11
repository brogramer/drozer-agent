package com.mwr.droidhg.agent.services;

import java.util.Locale;

import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

import com.mwr.common.logging.LogMessage;
import com.mwr.common.logging.Logger;
import com.mwr.droidhg.Agent;
import com.mwr.droidhg.agent.EndpointManager;
import com.mwr.droidhg.agent.R;
import com.mwr.droidhg.connector.Client;
import com.mwr.droidhg.connector.ConnectorParameters;
import com.mwr.droidhg.connector.Endpoint;

public class ClientService extends ConnectorService {
	
	public static final int MSG_GET_DETAILED_ENDPOINT_STATUS = 11;
	public static final int MSG_GET_ENDPOINTS_STATUS = 12;
	public static final int MSG_GET_SSL_FINGERPRINT = 13;
	public static final int MSG_START_ENDPOINT = 14;
	public static final int MSG_STOP_ENDPOINT = 15;
	
	private SparseArray<Client> clients = new SparseArray<Client>();
	private final EndpointManager endpoint_manager = new EndpointManager(this);
	
	private void createAndEnableEndpoint(Bundle bundle) {
		Log.i("ClientService", "reading attributes...");
		int id = -1;
		String name = "auto-endpoint-" + bundle.hashCode();
		String host = bundle.getString("host");
		int port = bundle.getInt("port");
		boolean ssl = bundle.getBoolean("ssl");
		String ssl_truststore_path = bundle.getString("ssl-truststore-path");
		String ssl_truststore_password = bundle.getString("ssl-truststore-password");
		String password = bundle.getString("password");
		
		Endpoint endpoint = new Endpoint(name, host, port, ssl, ssl_truststore_path, ssl_truststore_password, password);
		
		Log.i("ClientService", "created endpoint, saving...");
		if(this.endpoint_manager.add(endpoint)) {
			for(Endpoint e : this.endpoint_manager.all()) {
				if(e.getName().equals(name)) {
					id = e.getId();
					
					break;
				}
			}
			
			if(id > -1)
				this.startEndpoint(id);
			else
				Log.e("ClientService", "could not find endpoint");
		}
		else {
			Log.e("ClientService", "error creating endpoint");
		}
	}
	
	public Bundle getEndpointDetailedStatus(int endpoint_id) {
		Bundle data = new Bundle();
		Endpoint endpoint = this.endpoint_manager.get(endpoint_id);
		
		data.putInt(Endpoint.ENDPOINT_ID, endpoint.getId());
		data.putBoolean(Endpoint.CONNECTOR_ENABLED, endpoint.isEnabled());
		data.putBoolean(Endpoint.ENDPOINT_PASSWORD, endpoint.hasPassword());
    	data.putBoolean(Endpoint.ENDPOINT_SSL, endpoint.isSSL());
    	
    	switch(endpoint.getStatus()) {
    	case ACTIVE:
    		data.putBoolean(Endpoint.CONNECTOR_CONNECTED, true);
    		data.putBoolean(Endpoint.CONNECTOR_OPEN_SESSIONS, true);
    		break;
    		
    	case CONNECTING:
    		data.putBoolean(Endpoint.CONNECTOR_CONNECTED, false);
    		data.putBoolean(Endpoint.CONNECTOR_OPEN_SESSIONS, false);
    		break;
    		
    	case ONLINE:
    		data.putBoolean(Endpoint.CONNECTOR_CONNECTED, true);
    		data.putBoolean(Endpoint.CONNECTOR_OPEN_SESSIONS, false);
    		break;
    		
    	default:
    		data.putBoolean(Endpoint.CONNECTOR_CONNECTED, false);
    		data.putBoolean(Endpoint.CONNECTOR_OPEN_SESSIONS, false);
    		break;
    	}
    	
    	return data;
	}
	
	public Bundle getEndpointFingerprint(int id) {
		Bundle data = new Bundle();
		
		Client client = this.clients.get(id);
		
		if(client != null)
			data.putString(Endpoint.CONNECTOR_SSL_FINGERPRINT, client.getPeerCertificateFingerprint());
		else
			data.putString(Endpoint.CONNECTOR_SSL_FINGERPRINT, "No running client.");
		
		return data;
	}
	
	public Bundle getEndpointsStatus() {
		Bundle data = new Bundle();
		
		for(Endpoint e : this.endpoint_manager.all())
			data.putInt("endpoint-" + e.getId(), e.getStatus().ordinal());
		
		return data;
	}
	
	@Override
	public void handleMessage(Message msg) {
		Bundle data = msg.getData();
		
		switch(msg.what) {
		case MSG_GET_DETAILED_ENDPOINT_STATUS:
			try {
				Message message = Message.obtain(null, MSG_GET_DETAILED_ENDPOINT_STATUS);
				message.setData(this.getEndpointDetailedStatus(data.getInt(Endpoint.ENDPOINT_ID)));
				
				msg.replyTo.send(message);
			}
			catch(RemoteException e) {
				Log.e(this.getString(R.string.log_tag_client_service), e.getMessage());
			}
			break;
			
		case MSG_GET_ENDPOINTS_STATUS:
			try {
				Message message = Message.obtain(null, MSG_GET_ENDPOINTS_STATUS);
				message.setData(this.getEndpointsStatus());
				
				msg.replyTo.send(message);
			}
			catch(RemoteException e) {
				Log.e(this.getString(R.string.log_tag_client_service), e.getMessage());
			}
			break;
			
		case MSG_GET_SSL_FINGERPRINT:
			try {
				Message message = Message.obtain(null, MSG_GET_SSL_FINGERPRINT);
				message.setData(this.getEndpointFingerprint(data.getInt(Endpoint.ENDPOINT_ID)));
				
				msg.replyTo.send(message);
			}
			catch(RemoteException e) {
				Log.e(this.getString(R.string.log_tag_client_service), e.getMessage());
			}
			break;
			
		case MSG_START_ENDPOINT:
			try {
				this.startEndpoint(data.getInt(Endpoint.ENDPOINT_ID));
				
				Message message = Message.obtain(null, MSG_GET_ENDPOINTS_STATUS);
				message.setData(this.getEndpointsStatus());
				
				msg.replyTo.send(message);
			}
			catch(RemoteException e) {
				Log.e(this.getString(R.string.log_tag_client_service), e.getMessage());
			}
			break;
			
		case MSG_STOP_ENDPOINT:
			try {
				this.stopEndpoint(data.getInt(Endpoint.ENDPOINT_ID));
				
				Message message = Message.obtain(null, MSG_GET_ENDPOINTS_STATUS);
				message.setData(this.getEndpointsStatus());
				
				msg.replyTo.send(message);
			}
			catch(RemoteException e) {
				Log.e(this.getString(R.string.log_tag_client_service), e.getMessage());
			}
			break;
		}
	}
	
	@Override
	public void log(Logger logger, LogMessage msg) {
		Bundle data = new Bundle();
		data.putInt(Endpoint.ENDPOINT_ID, ((Endpoint)logger).getId());
		data.putBundle(ConnectorParameters.CONNECTOR_LOG_MESSAGE, msg.toBundle());
		
		this.broadcastLogMessageBundle(data);
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
		
		Agent.getInstance().setContext(this);
		ClientService.running = true;
		
		this.endpoint_manager.setOnEndpointStatusChangeListener(new EndpointManager.OnEndpointStatusChangeListener() {
			
			@Override
			public void onEndpointStopped(Endpoint endpoint) {}
			
			@Override
			public void onEndpointStarted(Endpoint endpoint) {}
			
			@Override
			public void onEndpointStatusChanged(Endpoint endpoint) {
				Message message = Message.obtain(null, MSG_GET_ENDPOINTS_STATUS);
				message.setData(ClientService.this.getEndpointsStatus());
				
				ClientService.this.sendToAllMessengers(message);
			}
			
		});
		
		for(Endpoint e : this.endpoint_manager.all())
			e.setStatus(Endpoint.Status.OFFLINE);
	}
	
	@Override
	public void onDestroy() {
		ClientService.running = false;
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		System.out.println("ClientService: onStartCommand: " + intent);
		
		if(intent.hasExtra("host"))
			this.createAndEnableEndpoint(intent.getExtras());
		
		return super.onStartCommand(intent, flags, startId);
	}
	
	public static void startAndBindToService(Context context, ServiceConnection serviceConnection) {
		if(!ClientService.running)
			context.startService(new Intent(context, ClientService.class));
		
		Intent intent = new Intent(context, ClientService.class);
    	context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
	}
	
	public void startEndpoint(int id) {
		if(this.clients.get(id) == null) {
			Endpoint endpoint = this.endpoint_manager.get(id, true);
			
			Client client = new Client(endpoint);
			client.setLogger(this);
			
			this.clients.put(id, client);
			
			endpoint.enabled = true;
			client.start();
			
			Toast.makeText(this, String.format(Locale.ENGLISH, this.getString(R.string.endpoint_started), endpoint.toConnectionString()), Toast.LENGTH_SHORT).show();
		}
	}
	
	public void stopEndpoint(int id) {
		Endpoint endpoint = this.endpoint_manager.get(id);
		Client client = this.clients.get(id);
		
		if(client != null) {
			client.stopConnector();
			endpoint.enabled = false;
			
			this.clients.remove(id);
			
			Toast.makeText(this, String.format(Locale.ENGLISH, this.getString(R.string.endpoint_stopped), endpoint.toConnectionString()), Toast.LENGTH_SHORT).show();
		}
	}

}

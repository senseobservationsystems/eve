package com.almende.eve.monitor;

import java.io.IOException;
import java.util.UUID;
import java.util.logging.Logger;

import com.almende.eve.agent.Agent;
import com.almende.eve.rpc.jsonrpc.JSONRPCException;
import com.almende.eve.rpc.jsonrpc.jackson.JOM;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class Push implements ResultMonitorConfigType {
	private static final long	serialVersionUID	= -6113822981521869299L;
	private static final Logger	LOG		= Logger.getLogger(Push.class
			.getCanonicalName());
	private String  pushId      = null;
	private int		interval	= -1;
	private boolean	onEvent		= false;
	private boolean	onChange	= false;
	private String	event		= "";
	
	public Push(int interval, boolean onEvent) {
		this.pushId = UUID.randomUUID().toString();
		this.interval = interval;
		this.onEvent = onEvent;
	}
	
	public Push() {
		this.pushId = UUID.randomUUID().toString();
	}
	
	public Push onInterval(int interval) {
		this.interval = interval;
		return this;
	}
	
	public Push onEvent() {
		this.onEvent = true;
		return this;
	}
	
	public Push onEvent(String event) {
		this.onEvent = true;
		this.event = event;
		return this;
	}
	
	public Push onChange() {
		this.onChange = true;
		return this;
	}
	
	public void init(ResultMonitor monitor, Agent agent) throws IOException, JSONRPCException
			 {
		ObjectNode wrapper = JOM.createObjectNode();
		ObjectNode pushParams = JOM.createObjectNode();
		
		pushParams.put("monitorId", monitor.getId());
		if (interval > 0) {
			pushParams.put("interval", interval);
		}
		pushParams.put("onEvent", onEvent);
		if (!event.equals("")) {
			pushParams.put("event", event);
		}
		pushParams.put("onChange", onChange);
		pushParams.put("method", monitor.getMethod());
		pushParams.put("params", monitor.getParams());
		
		wrapper.put("pushParams", pushParams);

		LOG.info("Registering push:"+monitor.getUrl());
		wrapper.put("pushId", pushId);

		monitor.getPushes().add(this);
		agent.sendAsync(monitor.getUrl(), "monitor.registerPush", wrapper,null,Void.class);
	}
	public void cancel(ResultMonitor monitor, Agent agent) throws IOException, JSONRPCException{
		ObjectNode params = JOM.createObjectNode();
		params.put("pushId",pushId);
		agent.sendAsync(monitor.getUrl(), "monitor.unregisterPush", params, null, Void.class);
	}

	public String getPushId() {
		return pushId;
	}

	public void setPushId(String pushId) {
		this.pushId = pushId;
	}

	public int getInterval() {
		return interval;
	}

	public void setInterval(int interval) {
		this.interval = interval;
	}

	public String getEvent() {
		return event;
	}

	public void setEvent(String event) {
		this.event = event;
	}
}
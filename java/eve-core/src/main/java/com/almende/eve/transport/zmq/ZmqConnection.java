package com.almende.eve.transport.zmq;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.zeromq.ZMQ.Poller;
import org.zeromq.ZMQ.Socket;

import com.almende.eve.agent.AgentHost;
import com.almende.eve.rpc.RequestParams;
import com.almende.eve.rpc.annotation.Sender;
import com.almende.eve.rpc.jsonrpc.JSONRPC;
import com.almende.eve.rpc.jsonrpc.JSONRPCException;
import com.almende.eve.rpc.jsonrpc.JSONRequest;
import com.almende.eve.rpc.jsonrpc.JSONResponse;
import com.almende.eve.rpc.jsonrpc.jackson.JOM;
import com.almende.eve.transport.AsyncCallback;
import com.almende.util.ObjectCache;
import com.almende.util.tokens.TokenRet;
import com.almende.util.tokens.TokenStore;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class ZmqConnection {
	private static final Logger	LOG			= Logger.getLogger(ZmqConnection.class
													.getCanonicalName());
	
	private final Socket		socket;
	private final String 		SIGADDR;
	private String				zmqUrl		= null;
	private Thread				myThread	= null;
	private AgentHost			host		= null;
	private String				agentId		= null;
	
	public ZmqConnection(Socket socket) {
		this.socket = socket;
		SIGADDR = "inproc://signal_"+UUID.randomUUID().toString();
	}
	
	public Socket getSocket() {
		return socket;
	}
	
	public String getZmqUrl() {
		return zmqUrl;
	}
	
	public void setZmqUrl(String zmqUrl) {
		this.zmqUrl = zmqUrl;
	}
	
	public Thread getMyThread() {
		return myThread;
	}
	
	public void setMyThread(Thread myThread) {
		this.myThread = myThread;
	}
	
	public AgentHost getHost() {
		return host;
	}
	
	public void setHost(AgentHost host) {
		this.host = host;
	}
	
	public String getAgentId() {
		return agentId;
	}
	
	public void setAgentId(String agentId) {
		this.agentId = agentId;
	}
	
	public String getAgentUrl() {
		return "zmq:" + getZmqUrl();
	}
	
	public void setAgentUrl(String agentUrl) {
		this.zmqUrl = agentUrl.replaceFirst("zmq:/?/?", "");
	}
	
	private void sig(){
		Socket sig = ZMQ.getSocket(ZMQ.REQ);
		sig.connect(SIGADDR);
		sig.send("1",0);
		sig.recv();
		sig.setLinger(0);
		sig.close();
	}
	
	private void sendResponse(Socket socket, final byte[] connId,
			final String response) {
		sig();
		synchronized (socket) {
			socket.send(connId, ZMQ.SNDMORE);
			socket.send(new byte[0], ZMQ.SNDMORE);
			socket.send(response);
			
			socket.notifyAll();
		}
	}
	
	private void sendResponse(Socket socket, final byte[] connId,
			final JSONResponse response) {
		sendResponse(socket, connId, response.toString());
	}
	
	private ByteBuffer[] getRequest(Socket socket) {
		byte[] res = null;
		synchronized (socket) {
			res = socket.recv(ZMQ.DONTWAIT);
		}
		ByteBuffer[] result = new ByteBuffer[5];
		if (res != null) {
			result[0] = ByteBuffer.wrap(res);
			socket.recv();
			result[1] = ByteBuffer.wrap(socket.recv());
			result[2] = ByteBuffer.wrap(socket.recv());
			result[3] = ByteBuffer.wrap(socket.recv());
			result[4] = ByteBuffer.wrap(socket.recv());
		}
		return result;
		
	}
	
	/**
	 * process an incoming zmq message.
	 * If the message contains a valid JSON-RPC request or response,
	 * the message will be processed.
	 * 
	 * @param packet
	 */
	public void listen() {
		myThread = new Thread(new Runnable() {
			
			@Override
			public void run() {
				Socket signal = ZMQ.getSocket(ZMQ.REP);
				signal.bind(SIGADDR);
				
				ZMQ.Poller items = new ZMQ.Poller (2);
				items.register(socket,Poller.POLLIN);
				items.register(signal,Poller.POLLIN);

				while (true) {
					synchronized(socket){
						items.poll(-1);
						
						if (signal.getEvents() == Poller.POLLIN){
							signal.recv();
							signal.send("ok",0);
							try {
								socket.wait();
							} catch (InterruptedException e) {}
						}
					}
					
					// Receive
					// connID|emptyDelimiter|ZMQ.NORMAL|senderUrl|tokenJson|body
					// or
					// connID|emptyDelimiter|ZMQ.HANDSHAKE|senderUrl|tokenJson|timestamp
					
					try {
						final ByteBuffer[] msg = getRequest(socket);
						if (msg[0] != null) {
							new Thread(new Runnable() {
								@Override
								public void run() {
									handleMsg(msg);
								}
							}).start();
						}
					} catch (Exception e) {
						LOG.log(Level.SEVERE,"Caught error:", e);
					}
				}
			}
		});
		myThread.start();
	}
	
	private void handleMsg(final ByteBuffer[] msg) {
		
		// Receive connID|emptyDelimiter|ZMQ.NORMAL|senderUrl|tokenJson|body
		// or connID|emptyDelimiter|ZMQ.HANDSHAKE|senderUrl|tokenJson|timestamp
		final byte[] connId = msg[0].array();
		try {
			final boolean handShake = Arrays.equals(msg[1].array(),
					ZMQ.HANDSHAKE);
			final String senderUrl = new String(msg[2].array());
			final String body = new String(msg[4].array());
			
			TokenRet token = null;
			try {
				token = JOM.getInstance().readValue(msg[3].array(),
						TokenRet.class);
			} catch (Exception e) {
				LOG.log(Level.WARNING, "Failed to parse token structure:"
						+ new String(msg[3].array()), e);
				return;
			}
			if (handShake) {
				String res = TokenStore.get(body);
				sendResponse(socket, connId, res);
				return;
			} else {
				ObjectCache sessionCache = ObjectCache.get("ZMQSessions");
				String key = senderUrl + ":" + token.getToken();
				if (!sessionCache.containsKey(key)
						&& JSONRPC
								.hasPrivate(host.getAgent(agentId).getClass())) {
					final String addr = senderUrl.replaceFirst("zmq:/?/?", "");
					final Socket locSocket = ZMQ.getSocket(ZMQ.REQ);
					locSocket.connect(addr);
					locSocket.send(ZMQ.HANDSHAKE, ZMQ.SNDMORE);
					locSocket.send(senderUrl, ZMQ.SNDMORE);
					locSocket.send(token.toString(), ZMQ.SNDMORE);
					locSocket.send(token.getTime());
					
					String result = new String(locSocket.recv());
					locSocket.setLinger(0);
					locSocket.close();
					if (token.getToken().equals(result)) {
						sessionCache.put(key, true);
					} else {
						LOG.warning("Failed to complete handshake!");
						return;
					}
				}
			}
			
			if (body != null && body.startsWith("{")
					|| body.trim().startsWith("{")) {
				// the body contains a JSON object
				ObjectNode json = null;
				json = JOM.getInstance().readValue(body, ObjectNode.class);
				
				JSONRequest request = new JSONRequest(json);
				invoke(senderUrl, request, new AsyncCallback<JSONResponse>() {
					
					@Override
					public void onSuccess(JSONResponse result) {
						sendResponse(socket, connId, result);
					}
					
					@Override
					public void onFailure(Exception e) {
						LOG.log(Level.WARNING, "Failure call", e);
						JSONRPCException jsonError = new JSONRPCException(
								JSONRPCException.CODE.INTERNAL_ERROR, e
										.getMessage(), e);
						JSONResponse response = new JSONResponse(jsonError);
						sendResponse(socket, connId, response);
					}
					
				});
			}
		} catch (Exception e) {
			LOG.log(Level.WARNING, "Failed to handle request", e);
			// generate JSON error response
			JSONRPCException jsonError = new JSONRPCException(
					JSONRPCException.CODE.INTERNAL_ERROR, e.getMessage(), e);
			JSONResponse response = new JSONResponse(jsonError);
			sendResponse(socket, connId, response);
		}
	}
	
	/**
	 * Invoke a JSON-RPC request
	 * Invocation is done in a separate thread to prevent blocking the
	 * single threaded XMPP PacketListener (which can cause deadlocks).
	 * 
	 * @param senderUrl
	 * @param request
	 */
	private void invoke(final String senderUrl, final JSONRequest request,
			final AsyncCallback<JSONResponse> callback) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				JSONResponse response;
				try {
					// append the sender to the request parameters
					RequestParams params = new RequestParams();
					params.put(Sender.class, senderUrl);
					
					// invoke the agent
					response = host.receive(agentId, request, params);
					callback.onSuccess(response);
					
				} catch (Exception err) {
					// generate JSON error response
					JSONRPCException jsonError = new JSONRPCException(
							JSONRPCException.CODE.INTERNAL_ERROR,
							err.getMessage(), err);
					
					callback.onFailure(jsonError);
				}
				
			}
		}).start();
	}
}

package com.almende.test.agents;

import com.almende.eve.agent.AgentInterface;
import com.almende.eve.rpc.annotation.Name;

public interface Test2AgentInterface extends AgentInterface {
	public Double add(@Name("a") Double a, @Name("b") Double b);
	public Double multiply(@Name("a") Double a, @Name("b") Double b);
	public Double increment();


	public enum STATUS {GOOD, BAD, OK, WRONG, FAILED, SUCCESS};
	public STATUS testEnum(@Name("status") STATUS status);
	
	public void testVoid();
}

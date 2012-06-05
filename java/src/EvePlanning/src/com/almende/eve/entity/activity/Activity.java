package com.almende.eve.entity.activity;

import java.io.Serializable;

import org.joda.time.DateTime;

@SuppressWarnings("serial")
public class Activity implements Serializable, Cloneable {
	public Activity() {
		setSummary(null);
		setConstraints(null);
		setStatus(null);
	}

	public void setSummary(String summary) {
		this.summary = summary;
	}
	
	public String getSummary() {
		return summary;
	}

	public void setConstraints(Constraints constraints) {
		this.constraints = constraints != null ? constraints : new Constraints();
	}

	public Constraints getConstraints() {
		return constraints;
	}

	public void setStatus(Status status) {
		this.status = status != null ? status : new Status();
	}

	public Status getStatus() {
		return status;
	}

	public void merge(Activity other) {
		if (other.summary != null) {
			summary = other.summary;
		}
		constraints.merge(other.constraints);
		status.merge(other.status);
	}
	
	public Activity clone() {
		Activity clone = new Activity();
		clone.summary = summary;
		clone.constraints = constraints.clone();
		clone.status = status.clone();
		
		return clone;
	}
	
	/**
	 * Check if a is after b
	 * @param a
	 * @param b
	 * @return
	 */
	public boolean isAfter(Activity other) {
		DateTime updatedThis = null;
		if (this.getStatus().getUpdated() != null) {
			updatedThis = new DateTime(this.getStatus().getUpdated());
		}
		DateTime updatedOther = null;
		if (other.getStatus().getUpdated() != null) {
			updatedOther = new DateTime(other.getStatus().getUpdated());
		}

		if (updatedOther == null) {
			// take this as newest
			return true;
		}
		else if (updatedThis == null) {
			// take other as newest
			return false;
		}
		else if (updatedThis.isAfter(updatedOther)) {
			// take this as newest
			return true;	
		}
		else {
			// take other as newest
			return false;
		}
	}
	
	public static Activity sync (Activity a, Activity b) {
		Activity clone;
		if (a.isAfter(b)) {
			clone = b.clone(); 
			clone.merge(a);
		}
		else {
			clone = a.clone();
			clone.merge(b);
		}

		return clone;
	}
	
	private String summary = "";
	private Constraints constraints = new Constraints();
	private Status status = new Status();
}
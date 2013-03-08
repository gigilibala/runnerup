/*
 * Copyright (C) 2012 - 2013 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.runnerup.workout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import org.runnerup.gpstracker.GpsTracker;
import org.runnerup.util.Constants.DB;

import android.content.ContentValues;

/**
 * This class is the top level object for a workout, it is being called by
 * RunActivity, and by the Workout components
 */
public class Workout implements WorkoutComponent {

	long lap = 0;
	int currentStepNo = -1;
	Step currentStep = null;
	ArrayList<Step> steps = new ArrayList<Step>();
	HashSet<Feedback> pendingFeedback = new HashSet<Feedback>();

	GpsTracker gpsTracker = null;

	public static final String KEY_TTS = "tts";
	public static final String KEY_COUNTER_VIEW = "CountdownView";
	
	public Workout() {
	}

	public void setGpsTracker(GpsTracker gpsTracker) {
		this.gpsTracker = gpsTracker;
	}

	public void onInit(Workout w, HashMap<String, Object> bindValues) {
		assert (w == this);
		for (Step a : steps) {
			a.onInit(this, bindValues);
		}
	}

	public void onEnd(Workout w) {
		assert (w == this);
		for (Step a : steps) {
			a.onEnd(this);
		}
	}

	public void onStart(Scope s, Workout w) {
		assert (w == this);

		initFeedback();

		currentStepNo = 0;
		if (steps.size() > 0) {
			currentStep = steps.get(currentStepNo);
		}

		if (currentStep != null) {
			currentStep.onStart(Scope.WORKOUT, this);
			currentStep.onStart(Scope.STEP, this);
			currentStep.onStart(Scope.LAP, this);
		}

		emitFeedback();
	}

	public void onTick() {
		initFeedback();

		while (currentStep != null) {
			boolean finished = currentStep.onTick(this);
			if (finished == false)
				break;

			onNextStep();
		}
		emitFeedback();
	}

	public void onNextStep() {
		currentStep.onComplete(Scope.LAP, this);
		currentStep.onComplete(Scope.STEP, this);
		currentStepNo++;
		if (currentStepNo < steps.size()) {
			currentStep = steps.get(currentStepNo);
			currentStep.onStart(Scope.STEP, this);
			currentStep.onStart(Scope.LAP, this);
		} else {
			currentStep.onComplete(Scope.WORKOUT, this);
			currentStep = null;
			gpsTracker.stop();
		}
	}

	
	public void onPause(Workout w) {

		gpsTracker.stop();

		initFeedback();
		if (currentStep != null) {
			currentStep.onPause(this);
		}
		emitFeedback();
	}

	public boolean isPaused() {
		return gpsTracker.isPaused();
	}

	public Step getCurrentStep() {
		return currentStep;
	}

	public void onNewLap() {
		initFeedback();
		if (currentStep != null) {
			currentStep.onComplete(Scope.LAP, this);
			currentStep.onStart(Scope.LAP, this);
		}
		emitFeedback();
	}

	public void onStop(Workout w) {

		gpsTracker.stop();

		initFeedback();
		if (currentStep != null) {
			currentStep.onStop(this);
		}
		emitFeedback();
	}

	public void onResume(Workout w) {
		gpsTracker.resume();

		initFeedback();
		if (currentStep != null) {
			currentStep.onResume(this);
		}
		emitFeedback();
	}

	public void onComplete(Scope s, Workout w) {
		if (currentStep != null) {
			currentStep.onComplete(Scope.LAP, this);
			currentStep.onComplete(Scope.STEP, this);
			currentStep.onComplete(Scope.WORKOUT, this);
		}
		currentStep = null;
		currentStepNo = -1;
	}

	public void onSave() {
		gpsTracker.completeActivity(true);
	}

	public void onDiscard() {
		gpsTracker.completeActivity(false);
	}

	public double get(Scope scope, Dimension d) {
		if (d == Dimension.DISTANCE)
			return getDistance(scope);
		else if (d == Dimension.TIME)
			return getTime(scope);
		else if (d == Dimension.SPEED)
			return getSpeed(scope);
		assert (false);
		return 0;
	}

	public long getDistance(Scope scope) {
		if (scope == Scope.WORKOUT)
			return (long) gpsTracker.getDistance();
		else if (currentStep != null) {
			return currentStep.getDistance(this, scope);
		}
		assert (false);
		return 0;
	}

	public long getTime(Scope scope) {
		if (scope == Scope.WORKOUT)
			return (long) gpsTracker.getTime();
		else if (currentStep != null) {
			return currentStep.getTime(this, scope);
		}
		assert (false);
		return 0;
	}

	public double getSpeed(Scope scope) {
		if (scope == Scope.WORKOUT) {
			long d = getDistance(scope);
			long t = getTime(scope);
			if (t == 0)
				return 0;
			return ((double) d) / ((double) t);
		} else if (currentStep != null) {
			return currentStep.getSpeed(this, scope);
		}
		assert (false);
		return 0;
	}

	public double getDuration(Scope scope, Dimension dimension) {
		if (scope == Scope.STEP && currentStep != null) {
			return currentStep.getDuration(dimension);
		}
		return 0;
	}

	public double getRemaining(Scope scope, Dimension dimension) {
		double curr = this.get(scope, dimension);
		double duration = this.getDuration(scope, dimension);
		if (duration > curr) {
			return duration - curr;
		} else {
			return 0;
		}
		
	}
	
	private void initFeedback() {
		pendingFeedback.clear();
	}

	public void addFeedback(Feedback f) {
		pendingFeedback.add(f);
	}

	private void emitFeedback() {
		for (Feedback f : pendingFeedback) {
			f.emit(this, gpsTracker.getApplicationContext());
		}
		pendingFeedback.clear();
	}

	void newLap(ContentValues tmp) {
		tmp.put(DB.LAP.LAP, lap);
		gpsTracker.newLap(tmp);
	}

	void saveLap(ContentValues tmp, boolean next) {
		gpsTracker.saveLap(tmp);
		if (next) {
			lap++;
		}
	}

	public int getStepCount() {
		return steps.size();
	}

	public Step getStep(int no) {
		return steps.get(no);
	}

	public boolean isLastStep() {
		return currentStepNo + 1 == steps.size();
	}

	public boolean isSimple() {
		if (steps.size() > 2)
			return false;
		if (steps.size() == 1)
			return true;
		return getStep(0).intensity == Intensity.RESTING; // activity countdown
	}
	
	private static class FakeWorkout extends Workout {
		
		FakeWorkout() {
			super();
		}
		
		public long getDistance(Scope scope) {
			switch(scope) {
			case WORKOUT:
				return (long) (3000 + 7000 * Math.random());
			case STEP:
				return (long) (300 + 700 * Math.random());
			case LAP:
				return (long) (300 + 700 * Math.random());
			}
			return 0;
		}

		public long getTime(Scope scope) {
			switch(scope) {
			case WORKOUT:
				return (long) (10 * 60 + 50 * 60 * Math.random());
			case STEP:
				return (long) (1 * 60 + 5 * 60 * Math.random());
			case LAP:
				return (long) (1 * 60 + 5 * 60 * Math.random());
			}
			return 0;
		}

		public double getSpeed(Scope scope) {
			long d = getDistance(scope);
			long t = getTime(scope);
			if (t == 0)
				return 0;
			return ((double) d) / ((double) t);
		}
	};
	
	public static Workout fakeWorkoutForTestingAudioCue() {
		FakeWorkout w = new FakeWorkout();
		return w;
	}
};

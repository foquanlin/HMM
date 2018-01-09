package hust.tools.hmm.learn;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import hust.tools.hmm.model.Emission;
import hust.tools.hmm.model.Transition;
import hust.tools.hmm.stream.SupervisedHMMSample;
import hust.tools.hmm.stream.SupervisedHMMSampleStream;
import hust.tools.hmm.utils.CommonUtils;
import hust.tools.hmm.utils.Dictionary;
import hust.tools.hmm.utils.Observation;
import hust.tools.hmm.utils.ObservationSequence;
import hust.tools.hmm.utils.State;
import hust.tools.hmm.utils.StateSequence;

/**
 *<ul>
 *<li>Description: 统计转移和发射的数量 
 *<li>Company: HUST
 *<li>@author Sonly
 *<li>Date: 2018年1月8日
 *</ul>
 */
public class TransitionAndEmissionCounter {
	
	/**
	 * 序列、发射的计数
	 */
	private HashMap<StateSequence, TransitionEntry> transitionCountMap;
	
	/**
	 * 状态、转移的计数
	 */
	private HashMap<State, EmissionEntry> emissionCountMap;
	
	/**
	 * 统计状态和观测的集合，并赋予索引
	 */
	private Dictionary dict;
	
	/**
	 * 默认的阈值
	 */
	private final static short DEFAULT_CUTOFF = 0;
	
	/**
	 * 默认HMM阶数
	 */
	private final static short DEFAULT_ORDER = 1; 
	
	public TransitionAndEmissionCounter() {
		init();
	}
	
	public TransitionAndEmissionCounter(SupervisedHMMSampleStream<?> sampleStream) throws IOException {
		this(sampleStream, DEFAULT_ORDER, DEFAULT_CUTOFF);
	}
	
	public TransitionAndEmissionCounter(SupervisedHMMSampleStream<?> sampleStream, int order) throws IOException {
		this(sampleStream, order, DEFAULT_CUTOFF);
	}
	
	public TransitionAndEmissionCounter(SupervisedHMMSampleStream<?> sampleStream, int order, int cutoff) throws IOException {
		if(order < 1 || cutoff < 0)
			throw new IllegalArgumentException("模型阶数和阈值应为正整数： order = " + order + ", cutoff = " + cutoff);
			
		init();
		
		SupervisedHMMSample sample = null;
		while((sample = (SupervisedHMMSample) sampleStream.read()) != null) {
			update(sample, order);
		}
		
		cutoff(cutoff);
	}
	
	public TransitionAndEmissionCounter(List<SupervisedHMMSample> samples) {
		this(samples, DEFAULT_ORDER, DEFAULT_CUTOFF);
	}
	
	public TransitionAndEmissionCounter(List<SupervisedHMMSample> samples, int order) {
		this(samples, order, DEFAULT_CUTOFF);
	}
	
	public TransitionAndEmissionCounter(List<SupervisedHMMSample> samples, int order, int cutoff) {
		if(order < 1 || cutoff < 0)
			throw new IllegalArgumentException("模型阶数和阈值应为正整数： order = " + order + ", cutoff = " + cutoff);
		
		init();
		
		for(SupervisedHMMSample sample : samples)
			update(sample, order);
		
		cutoff(cutoff);
	}
	
	public void update(SupervisedHMMSample sample, int order) {
		StateSequence stateSequence = sample.getStateSequence();
		stateSequence = dict.add(stateSequence);
		
		ObservationSequence observationSequence = sample.getObservationSequence();
		observationSequence = dict.add(observationSequence);
		
		for(int i = 1; i <= order + 1; i++) {//遍历n元的阶数，将隐藏序列切分成不同阶的n元序列
			List<State[]> list = CommonUtils.generate(stateSequence, i);
			for(int j = 0; j < list.size(); j++) {//遍历n元序列
				State[] states = list.get(j);
				
				if(states.length < order + 1)//统计n元序列的计数, 最高阶的状态序列不需要统计
					add(new StateSequence(states));
				
				if(states.length > 1) {//统计隐藏状态之间的转移计数
					State[] temp = new State[states.length - 1];
					int index = 0;
					for(index = 0; index < temp.length; index++)
						temp[index] = states[index];
					
					StateSequence start = new StateSequence(temp);
					State target = states[i - 1];
					add(start, target);
				}
				
				//统计隐藏状态到观测状态的发射计数
				if(states.length == 1) {//当为终点发射时，state大小为1，当为边发射时为2
					State state = states[0];
					Observation observation = observationSequence.get(j + i - 1);
					add(state, observation);
				}
			}
		}
	}
	
	private void init() {
		dict = new Dictionary();
		transitionCountMap = new HashMap<>();
		emissionCountMap = new HashMap<>();
	}
	
	/**
	 * 增加一个状态序列
	 * @param sequence	待增加的状态序列
	 */
	private void add(StateSequence sequence) {
		if(transitionCountMap.containsKey(sequence))
			transitionCountMap.put(sequence, transitionCountMap.get(sequence).setCount(transitionCountMap.get(sequence).getCount() + 1));
		else {
			TransitionEntry entry = new TransitionEntry();
			transitionCountMap.put(sequence, entry.setCount(1L));
		}
	}
	
	/**
	 * 增加一个起点为starts，终点为target的转移
	 * @param starts	转移的起点
	 * @param target	转移的终点
	 */
	private void add(StateSequence starts, State target) {
		if(transitionCountMap.containsKey(starts)) {
			TransitionEntry entry = transitionCountMap.get(starts);
			entry.add(target);
			transitionCountMap.put(starts, entry);
		}else {
			TransitionEntry entry = new TransitionEntry();
			entry.setCount(1L);
			entry.add(target);
			transitionCountMap.put(starts, entry);
		}
	}
	
	/**
	 * 增加一个状态为state，观测为observation的发射
	 * @param state			发射的状态
	 * @param observation	发射的观测
	 */
	private void add(State state, Observation observation) {
		if(emissionCountMap.containsKey(state)) {
			EmissionEntry entry = emissionCountMap.get(state);
			entry.setCount(entry.getCount() + 1);
			entry.add(observation);
			emissionCountMap.put(state, entry);
		}else {
			EmissionEntry entry = new EmissionEntry();
			entry.setCount(1L);
			entry.add(observation);
			emissionCountMap.put(state, entry);
		}
	}
	
	/**
	 * 返回隐藏状态序列的数量
	 * @param sequence	待返回数量的隐藏状态序列
	 * @return			隐藏状态序列的数量
	 */
	public long getSequeceCount(StateSequence sequence) {
		if(transitionCountMap.containsKey(sequence))
			return transitionCountMap.get(sequence).getCount();
		
		return 0;
	}
	
	/**
	 * 返回起点为start，终点为target的转移数量
	 * @param start		转移的起点
	 * @param target	转移的终点
	 * @return			转移的数量
	 */
	public long getTransitionCount(StateSequence start, State target) {
		if(transitionCountMap.containsKey(start))
			return transitionCountMap.get(start).getTransitionCount(target);
		
		return 0;
	}
	
	/**
	 * 返回给定转移的数量
	 * @param transition	转移
	 * @return				转移的数量
	 */
	public long getTransitionCount(Transition transition) {
		return getTransitionCount(transition.getStartSequence(), transition.getTarget());
	}

	/**
	 * 返回状态为state，观测为observation的发射的数量
	 * @param state			发射的状态
	 * @param observation	发射的观测
	 * @return				发射的数量
	 */
	public long getEmissionCount(State state, Observation observation) {
		if(emissionCountMap.containsKey(state))
			return emissionCountMap.get(state).getEmissionCount(observation);
		
		return 0;
	}
	
	/**
	 * 返回给定发射的数量
	 * @param emission	发射
	 * @return			发射的数量
	 */
	public long getEmissionCount(Emission emission) {
		return getEmissionCount(emission.getState(), emission.getObservation());
	}
	
	/**
	 * 返回是否包含状态序列
	 * @param sequence	待判断的状态序列
	 * @return			true-包含/false-不包含
	 */
	public boolean contain(StateSequence sequence) {
		return transitionCountMap.containsKey(sequence);
	}
	
	/**
	 * 返回是否包含以state为发射的状态的记录
	 * @param state	待判断的发射
	 * @return		true-包含/false-不包含
	 */
	public boolean contain(State state) {
		return emissionCountMap.containsKey(state);
	}
	
	/**
	 * 返回是否包含发射emission
	 * @param emission	待判断的发射
	 * @return			true-包含/false-不包含
	 */
	public boolean contain(Emission emission) {
		return contain(emission.getState(), emission.getObservation());
	}
	
	/**
	 * 返回是否包含状态为state，观测为observation的发射
	 * @param state			发射的状态
	 * @param observation	发射的观测
	 * @return				true-包含/false-不包含
	 */
	public boolean contain(State state, Observation observation) {
		if(contain(state))
			return emissionCountMap.get(state).contain(observation);
		
		return false;
	}
	
	/**
	 * 返回是否包含状态转移transition
	 * @param transition	待判断的状态转移
	 * @return				true-包含/false-不包含
	 */
	public boolean contain(Transition transition) {
		return contain(transition.getStartSequence(), transition.getTarget());
	}
	
	/**
	 * 返回是否包含起点为start，终点为target的转移
	 * @param starts	转移的起点
	 * @param target	转移的终点
	 * @return			true-包含/false-不包含
	 */
	public boolean contain(StateSequence starts, State target) {
		if(contain(starts)) 
			return transitionCountMap.get(starts).contain(target);
		
		return false;
	}

	public void cutoff(int cutoff) {
		for (Iterator<Entry<StateSequence, TransitionEntry>> its = transitionCountMap.entrySet().iterator(); its.hasNext();){
		    Entry<StateSequence, TransitionEntry> entrys = its.next();
		    TransitionEntry transitionEntry = entrys.getValue();
		    
		    for (Iterator<Entry<State, Long>> it = transitionEntry.iterator(); it.hasNext();){
		    	 Entry<State, Long> stateEntry = it.next();
		    	 if(stateEntry.getValue() < cutoff) {
		    		 transitionEntry.setCount(transitionEntry.getCount() - stateEntry.getValue());
		    		 it.remove();
		    	 }
		    }
		    
		    if(transitionEntry.size() == 0)
		    	its.remove();
		}
		
		for (Iterator<Entry<State, EmissionEntry>> its = emissionCountMap.entrySet().iterator(); its.hasNext();){
		    Entry<State, EmissionEntry> entrys = its.next();
		    EmissionEntry emissionEntry = entrys.getValue();
		    
		    for (Iterator<Entry<Observation, Long>> it = emissionEntry.iterator(); it.hasNext();){
		    	 Entry<Observation, Long> observationEntry = it.next();
		    	 if(observationEntry.getValue() < cutoff) {
		    		 emissionEntry.setCount(emissionEntry.getCount() - observationEntry.getValue());
		    		 it.remove();
		    	 }
		    }
		    
		    if(emissionEntry.size() == 0)
		    	its.remove();
		}
	}
}
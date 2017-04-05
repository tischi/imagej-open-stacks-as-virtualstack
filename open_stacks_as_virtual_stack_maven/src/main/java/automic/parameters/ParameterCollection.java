package automic.parameters;

import java.util.LinkedHashMap;
import java.util.Set;

public class ParameterCollection{

	LinkedHashMap<String, Parameter> parameters;		//here parameters are stored, and accessed by the key
											//?is it better to store parameterID inside structure
	public ParameterCollection(){
		parameters=new LinkedHashMap<String,Parameter>();
	}
	
	public void addParameter(String _addKey,Object _addValue,Object _addDefaultValue, String _addType){
		if(hasParameterKey(_addKey))
			throw new RuntimeException(String.format("Can not add parameter with the key %s because it already exists",_addKey));
		parameters.put(_addKey, new Parameter(_addValue,_addDefaultValue,_addType));
	}
	
	/**
	 * sets new value for the parameter. Assumes that parameter with such key exists
	 * @param _key
	 * @param _newValue supposed to be of the right type. Type matching will be checked upon assignment.
	 */
	public void setParameterValue(String _key, Object _newValue){
		Parameter targetParameter=parameters.get(_key);
		if(targetParameter==null)
			throw new RuntimeException(String.format("No parameter with key %s",_key));
		targetParameter.setValue(_newValue);
	}
	
	/**
	 * parses the provided string to the parameter value. 
	 * @param _key identifier of the parameter. Assumed to exist
	 * @param _newStringValue 
	 */
	public void setParameterValueFromString(String _key, String _newStringValue)throws Exception{
		Parameter targetParameter=parameters.get(_key);
		if(targetParameter==null)
			throw new Exception(String.format("No parameter with key %s",_key));
		targetParameter.setValueFromString(_newStringValue);
	}

	public boolean hasParameterKey(String _testKey){
		return parameters.containsKey(_testKey);
	}
	
	public void setUndefinedValuesFromDefaults(){
		for (Parameter p:parameters.values())
			p.setUnderfinedValueFromDefault();
	}
	
	public int getnumberOfParameters(){
		return parameters.size();
	}
	
	public Set<String> getParametersIndetifiers(){
		return parameters.keySet();
	}
	
	public Object getParameterValue(String _parameterKey){
		return parameters.get(_parameterKey).getValue();
	}
	
	public String getParameterStringValue(String _parameterKey){
		return parameters.get(_parameterKey).getStringValue();
	}
	
	public Parameter getParameterObject(String _parameterKey){
		return parameters.get(_parameterKey);
	}
}

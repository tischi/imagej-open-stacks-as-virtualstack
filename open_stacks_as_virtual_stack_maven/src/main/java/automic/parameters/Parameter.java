package automic.parameters;


/**
 * Class representing values of the parameters and their types
 * Currently supposed 
 * @author Aliaksandr Halavatyi
 *
 */

public class Parameter {
	Object value=null;
	Object defaultValue=null;
	String type="";
	
	/**
	 * create parameter by setting both actual and default values, both can be null 
	 * @param _value
	 * @param _defaultValue
	 * @param _type
	 */
	public Parameter (Object _value, Object _defaultValue, String _type){
		if(!ParameterType.isClassifiedType(_type))
			throw new ClassCastException("Can not instantiate parameter boject because underfined type identificator is provided");
		type=_type;
		
		checkNotNullValueForType(_value,null);
		value=_value;
		checkNotNullValueForType(_defaultValue,null);
		defaultValue=_defaultValue;
	}
	
	public Object getDefaultValue(){
		return defaultValue;
	}
	
	public Object getValue(){
		return value;
	}
	
	public String getStringValue(){
		return ParameterType.convertToString(value, type);
	}
	
	public void setValue(Object _newValue){
		checkValueForType(_newValue,null);
		value=_newValue;
	}

	/**
	 * set parameter value. null or type-incompatible argument will produce runtime error
	 * @param _newValue
	 */
	public void setValueFromString(String _newValue){
		Object convertedValue=ParameterType.parseToObject(_newValue, type);
		if(convertedValue==null)
			throw new IllegalArgumentException("Can not cast Provided string to the meaningful parameter value");
		value=convertedValue;
	}

	
	public boolean isDefined(){
		return value!=null;
	}

	public boolean hasDefault(){
		return defaultValue!=null;
	}
	
	public void setUnderfinedValueFromDefault(){
		if((!isDefined())&&(hasDefault()))
			value=defaultValue;
	}
	
	private void checkValueForType(Object _value,String _errorMessage){
		if (!ParameterType.isTypeMatching(_value, type))
			throw new ClassCastException((_errorMessage==null)?"Can not store partameter value because provided object does not match specified type":_errorMessage);
	}

	
	private void checkNotNullValueForType(Object _value,String _errorMessage){
		if(_value!=null)
			checkValueForType(_value,_errorMessage);
	}


}

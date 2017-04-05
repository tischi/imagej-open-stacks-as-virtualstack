package automic.parameters;

import java.util.Arrays;
import java.util.Collection;

/**
 * 
 * Abstract class that defines types of possible parameters in two representations: strings and integers
 * It specifies conversions between these two types and and corresponding conversions of the values. 
 *
 */
public abstract class ParameterType{

	public static final String STRING_PARAMETER		="STRING";
	public static final String INT_PARAMETER		="INT";
	public static final String DOUBLE_PARAMETER		="DOUBLE";
	public static final String BOOL_PARAMETER		="BOOL";

	public static final Collection<String> PARAMETER_IDENTIFIERS=Arrays.asList(STRING_PARAMETER,INT_PARAMETER,DOUBLE_PARAMETER,BOOL_PARAMETER);
	
	
	public static String convertToString(Object _value,String _type){
		if(!isTypeMatching(_value, _type)) return null;
		return String.valueOf(_value);
	}
	
	public static Object parseToObject(String _stringValue,String _type){
		if(_stringValue==null) return null;
		switch(_type){
			case STRING_PARAMETER:	return _stringValue;
			case INT_PARAMETER:		return Integer.valueOf(_stringValue);
			case DOUBLE_PARAMETER:	return Double.valueOf(_stringValue);
			case BOOL_PARAMETER:	return Boolean.valueOf(_stringValue);
		}
		
		return null;
	}
	
	public static boolean isTypeMatching(Object _value,String _type){
		if(_value==null) return false;
		switch(_type){
			case STRING_PARAMETER:	return _value instanceof String;
			case INT_PARAMETER:		return _value instanceof Integer;
			case DOUBLE_PARAMETER:	return _value instanceof Double;
			case BOOL_PARAMETER:	return _value instanceof Boolean;
		}
		return false;
	}
	
	public static boolean isClassifiedType(String _type){
		return PARAMETER_IDENTIFIERS.contains(_type);
	}
	
	public String getTypeByValue(Object _value){
		if 		(_value==null)				return null;
		else if	(_value instanceof String)	return STRING_PARAMETER;
		else if	(_value instanceof Integer)	return INT_PARAMETER;
		else if	(_value instanceof Double)	return DOUBLE_PARAMETER;
		else if	(_value instanceof Boolean)	return BOOL_PARAMETER;
		else 								return "";
			
	}
}

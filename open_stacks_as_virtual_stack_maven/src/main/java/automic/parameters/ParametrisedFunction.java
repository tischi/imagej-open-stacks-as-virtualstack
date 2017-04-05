package automic.parameters;

public interface ParametrisedFunction {
	ParameterCollection createParameterCollection();
	
	void parseInputSettingValues(ParameterCollection _stepParameterCollection);

}

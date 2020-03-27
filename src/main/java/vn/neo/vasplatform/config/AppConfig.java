package vn.neo.vasplatform.config;

import com.neo.jdbc.ConnectDatabase;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Scope;

@Configuration
public class AppConfig {

    @Bean("propertiesConfiguration")
    @Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
    public PropertiesConfiguration loadPropertiesConfiguration() throws ConfigurationException {
        System.out.println("loadPropertiesConfiguration");
        PropertiesConfiguration propertiesConfiguration = new PropertiesConfiguration();
        propertiesConfiguration.setDelimiterParsingDisabled(true);
        propertiesConfiguration.setEncoding("UTF8");
        propertiesConfiguration.setPath("config/app.properties");
        propertiesConfiguration.load();
        // set properties auto load while file changed
        propertiesConfiguration.setReloadingStrategy(new FileChangedReloadingStrategy());

        return propertiesConfiguration;
    }

    @Autowired
    @Qualifier("propertiesConfiguration")
    private PropertiesConfiguration props;

    @Bean("connectDatabase")
    @DependsOn(value = {"propertiesConfiguration"})
    @Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
    public ConnectDatabase getConnectionDatabase() {
        return new ConnectDatabase(props);
    }
}

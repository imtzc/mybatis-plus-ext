package com.tangzc.mpe.conf;

import com.tangzc.mpe.BaseEntityFieldTypeHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * @author don
 */
@Configuration
@Import({
        BaseEntityFieldTypeHandler.class
})
public class PlatformMybatisAutoConfig {

}

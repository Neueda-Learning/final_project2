package com.portfoliomanager.worker.provider;

import com.portfoliomanager.worker.MarketDataProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MarketDataProviderConfiguration {

    @Bean
    @ConditionalOnProperty(
            name = "market-data.provider",
            havingValue = "alpaca",
            matchIfMissing = true)
    public MarketDataProvider alpacaWithTwelveDataFallback(
            MarketDataProperties properties) {
        return new FailoverMarketDataProvider(
                new AlpacaMarketDataProvider(properties),
                new TwelveDataProvider(properties));
    }
}

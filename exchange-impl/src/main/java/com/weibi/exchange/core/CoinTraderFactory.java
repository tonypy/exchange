package com.weibi.exchange.core;

import com.weibi.config.api.TradePairConfigService;
import com.weibi.config.model.TradePairConfig;
import com.weibi.core.commons.logging.ILogging;
import com.weibi.exchange.util.ExchangeKafkaUtil;
import com.weibi.exchange.util.LocalCacheUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 创建交易对工厂
 *
 * @author jsong
 */
@Component
public class CoinTraderFactory implements ILogging {

	/***缓存当前交易对配置***/
	private ConcurrentHashMap<String, CoinTrader> traderMap = new ConcurrentHashMap<>();
	@Autowired
	private TradePairConfigService tradePairConfigService;
	@Autowired
	private ExchangeKafkaUtil exchangeKafkaUtil;
	@Autowired
	private ExchangeBusiness exchangeBusiness;
	@Autowired
	private LocalCacheUtil localCacheUtil;
	@Value("${free.userExchangeFeeAccounts:2018060921480053731000008}")
	private String userExchangeFeeAccount;

	/**
	 * 获取交易器
	 *
	 * @param pair
	 * @return
	 */
	public CoinTrader getCoinTrader(String pair) {
		if (StringUtils.isEmpty(pair)) {
			return null;
		}

		CoinTrader coinTrader = traderMap.get(pair);
		if (coinTrader != null) {
			return coinTrader;
		}

		//防止重复加载
		synchronized (this) {
			TradePairConfig tradePairConfig = tradePairConfigService.getPairConfigByPair(pair);
			if (tradePairConfig == null) {
				return null;
			}
			coinTrader = traderMap.get(pair);
			if (coinTrader != null) {
				return coinTrader;
			}

			CoinTrader newCointrader = new CoinTrader(tradePairConfig.getPair());
			newCointrader.setPricePrecision(tradePairConfig.getDefaultPrecision());
			newCointrader.setAmountPrecision(tradePairConfig.getAmountPrecision());
			newCointrader.setMinimumTradeAmount(tradePairConfig.getMinimumTradeVolume());
			newCointrader.setUserExchangeFeeAccount(getRandomUserExchangeFeeAccount(userExchangeFeeAccount));
			newCointrader.setExchangeBusiness(exchangeBusiness);
			//newCointrader.setExchangeKafkaUtil(exchangeKafkaUtil);
			newCointrader.setLocalCacheUtil(localCacheUtil);
			traderMap.put(pair, newCointrader);
			return newCointrader;
		}
	}

	private String getRandomUserExchangeFeeAccount(String userExchangeFeeAccount) {
		String[] userAccount = userExchangeFeeAccount.split(",");
		return userAccount[new Random().nextInt(userAccount.length)];
	}

	public ConcurrentHashMap<String, CoinTrader> coinTraderMap() {
		return traderMap;
	}
}

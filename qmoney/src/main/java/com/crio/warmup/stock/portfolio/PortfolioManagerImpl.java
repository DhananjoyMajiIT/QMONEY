
package com.crio.warmup.stock.portfolio;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.SECONDS;

import com.crio.warmup.stock.dto.AnnualizedReturn;
import com.crio.warmup.stock.dto.Candle;
import com.crio.warmup.stock.dto.PortfolioTrade;
import com.crio.warmup.stock.dto.TiingoCandle;
import com.crio.warmup.stock.exception.StockQuoteServiceException;
import com.crio.warmup.stock.quotes.StockQuotesService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.springframework.web.client.RestTemplate;

public class PortfolioManagerImpl implements PortfolioManager {

    private RestTemplate restTemplate;
    private StockQuotesService stockQuotesService;
    private String token="56ced5d79d2bfeedeb35aa03a11d6d68a33074f7";



  // Caution: Do not delete or modify the constructor, or else your build will break!
  // This is absolutely necessary for backward compatibility
  protected PortfolioManagerImpl(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }


  //TODO: CRIO_TASK_MODULE_REFACTOR
  // 1. Now we want to convert our code into a module, so we will not call it from main anymore.
  //    Copy your code from Module#3 PortfolioManagerApplication#calculateAnnualizedReturn
  //    into #calculateAnnualizedReturn function here and ensure it follows the method signature.
  // 2. Logic to read Json file and convert them into Objects will not be required further as our
  //    clients will take care of it, going forward.

  // Note:
  // Make sure to exercise the tests inside PortfolioManagerTest using command below:
  // ./gradlew test --tests PortfolioManagerTest

  //CHECKSTYLE:OFF

  public PortfolioManagerImpl(StockQuotesService stockQuotesService) {
    this.stockQuotesService = stockQuotesService;
  }






  private Comparator<AnnualizedReturn> getComparator() {
    return Comparator.comparing(AnnualizedReturn::getAnnualizedReturn).reversed();
  }

  //CHECKSTYLE:OFF

  // TODO: CRIO_TASK_MODULE_REFACTOR
  //  Extract the logic to call Tiingo third-party APIs to a separate function.
  //  Remember to fill out the buildUri function and use that.


  public List<Candle> getStockQuote(String symbol, LocalDate from, LocalDate to)throws StockQuoteServiceException, JsonProcessingException {
    //  String tiingoRestURL = buildUri(symbol, from, to);
    // TiingoCandle[] tiingoCandleArray =
    // restTemplate.getForObject(tiingoRestURL, TiingoCandle[].class);
    // if (tiingoCandleArray == null)
    // return new ArrayList<>();
    // return Arrays.stream(tiingoCandleArray).collect(Collectors.toList());
    return stockQuotesService.getStockQuote(symbol, from, to);
  }

  protected  String buildUri(String symbol, LocalDate startDate, LocalDate endDate) {
      //  String uriTemplate = "https:api.tiingo.com/tiingo/daily/$SYMBOL/prices?"
      //       + "startDate=$STARTDATE&endDate=$ENDDATE&token=$APIKEY";
        return "https://api.tiingo.com/tiingo/daily/" + symbol + "/prices?startDate=" + startDate + "&endDate=" + endDate + "&token=" + token;
  }
  private Double getOpeningPriceOnStartDate(List<Candle> candles) {
    return candles.get(0).getOpen();
  }

  private Double getClosingPriceOnEndDate(List<Candle> candles) {
    return candles.get(candles.size() - 1).getClose();
  }
  public static AnnualizedReturn calculateAnnualizedReturns(LocalDate endDate, PortfolioTrade trade, Double buyPrice, Double sellPrice) {

      double total_num_years = ChronoUnit.DAYS.between(trade.getPurchaseDate(), endDate) / 365.2422;
      double totalReturns = (sellPrice - buyPrice) / buyPrice;
      double annualized_returns = Math.pow((1.0 + totalReturns), (1.0 / total_num_years)) - 1;
      return new AnnualizedReturn(trade.getSymbol(), annualized_returns, totalReturns);
      
  }
  @Override
  public List<AnnualizedReturn> calculateAnnualizedReturn(List<PortfolioTrade> portfolioTrades,LocalDate endDate) throws  StockQuoteServiceException {
    List<AnnualizedReturn> annualizedReturns = new ArrayList<>();
    for (PortfolioTrade portfolioTrade : portfolioTrades) {
      List<Candle> candles = new ArrayList<>();
      try {
        candles = getStockQuote(portfolioTrade.getSymbol(), portfolioTrade.getPurchaseDate(), endDate);
      } catch (JsonProcessingException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      AnnualizedReturn annualizedReturn = calculateAnnualizedReturns(endDate, portfolioTrade,
          getOpeningPriceOnStartDate(candles), getClosingPriceOnEndDate(candles));
      annualizedReturns.add(annualizedReturn);
    }
    return annualizedReturns.stream().sorted(getComparator()).collect(Collectors.toList());
  }


  @Override
  public List<AnnualizedReturn> calculateAnnualizedReturnParallel(
      List<PortfolioTrade> portfolioTrades, LocalDate endDate, int numThreads)
      throws StockQuoteServiceException {
        ExecutorService executorService = Executors.newFixedThreadPool(numThreads);
        List<AnnualizedReturn> annualizedReturns = new ArrayList<>();
        List<AnnualizedReturnTask> annualizedReturnTaskList = new ArrayList<>();
        List<Future<AnnualizedReturn>> annualizedReturnFutureList = null;
        for (PortfolioTrade portfolioTrade : portfolioTrades)
          annualizedReturnTaskList
              .add(new AnnualizedReturnTask(portfolioTrade, stockQuotesService, endDate));
        try {
          annualizedReturnFutureList = executorService.invokeAll(annualizedReturnTaskList);
        } catch (InterruptedException e) {
          throw new StockQuoteServiceException(e.getMessage());
        }
        for (Future<AnnualizedReturn> annualizedReturnFuture : annualizedReturnFutureList) {
          try {
            annualizedReturns.add(annualizedReturnFuture.get());
          } catch (InterruptedException | ExecutionException e) {
            throw new StockQuoteServiceException(e.getMessage());
          }
        }
        executorService.shutdown();
        return annualizedReturns.stream().sorted(getComparator()).collect(Collectors.toList());
  }
}

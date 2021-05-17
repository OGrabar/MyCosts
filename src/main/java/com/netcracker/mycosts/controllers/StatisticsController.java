package com.netcracker.mycosts.controllers;


import com.netcracker.mycosts.activities.MonthReportActivity;
import com.netcracker.mycosts.entities.*;
import com.netcracker.mycosts.services.MonthCostsService;
import com.netcracker.mycosts.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
public class StatisticsController {

    private MonthCostsService monthCostsService;
    private UserService userService;
    private MonthReportActivity monthReportActivity;

    @GetMapping("/last-month-stat")
    public ResponseEntity<Map<String, Double>> getStatisticForLastMonth(@AuthenticationPrincipal User user) {
        LocalDate startDate = LocalDate.of(LocalDate.now().getYear(), LocalDate.now().getMonth(), 1);
        Map<String, Double> monthCostsMap = monthCostsService.findMonthCostsByUserAndStartDate(user, startDate).stream()
                .filter(monthCosts -> monthCosts.getAmountUSD() > 0)
                .collect(Collectors.groupingBy(monthCosts -> monthCosts.getCategory().getName(),
                        Collectors.summingDouble(MonthCosts::getAmountUSD)));
        return ResponseEntity.status(HttpStatus.OK).body(monthCostsMap);
    }

    @GetMapping("/year-stat")
    public ResponseEntity<List<CategoryAmounts>> getStatistic(@AuthenticationPrincipal User user) {
        user = userService.getUserById(user.getId());
        Set<Category> userCategories = user.getCategories();
        LocalDate startDate = LocalDate.of(LocalDate.now().minusYears(1).getYear(),
                LocalDate.now().plusMonths(1).getMonth(), 1);
        System.out.println(startDate);
        List<MonthCosts> monthCostsList = monthCostsService.findMonthCostsByUser(user).stream()
                .filter(monthCosts -> monthCosts.getStartDate().compareTo(startDate) > 0)
                .sorted(Comparator.comparing(MonthCosts::getStartDate))
                .collect(Collectors.toList());
        List<CategoryAmounts> categoryAmountsList = new ArrayList<>();
        userCategories.forEach(category -> {
            final List<Double> amountsByCategoryFromDate = getAmountsByCategoryFromDate(category, startDate, monthCostsList);
            CategoryAmounts categoryAmounts = new CategoryAmounts(category.getName(), amountsByCategoryFromDate);
            categoryAmountsList.add(categoryAmounts);
        });
        return ResponseEntity.status(HttpStatus.OK).body(categoryAmountsList);
    }

    @GetMapping("/year-months")
    public ResponseEntity<List<String>> getMonthsOfLastYear() {
        List<String> dates = new ArrayList<>();
        LocalDate date = LocalDate.of(LocalDate.now().minusYears(1).getYear(),
                LocalDate.now().plusMonths(1).getMonth(), 1);

        while (date.compareTo(LocalDate.now()) <= 0) {
            String month = (date.getDayOfMonth() > 9) ? ("" + date.getMonthValue()) : ("0" + date.getMonthValue());
            String dateString = month + "/01/" + date.getYear() + " GMT";
            dates.add(dateString);
            date = date.plusMonths(1);
        }

        return ResponseEntity.status(HttpStatus.OK).body(dates);

    }

    @GetMapping("/totals")
    public ResponseEntity<Map<String, Double>> getAverages(@AuthenticationPrincipal User user) {
        user = userService.getUserById(user.getId());
        final List<MonthCosts> monthCostsByUser = monthCostsService.findMonthCostsByUser(user);
        Set<Category> categories = user.getCategories();
        Map<String, Double> monthCostsMap = new HashMap<>();
        categories.forEach(category -> {
            Map<String, Double> monthCostsAverageByCategory = getMonthCostsTotalByCategory(monthCostsByUser, category);
            monthCostsMap.putAll(monthCostsAverageByCategory);
        });
        return ResponseEntity.status(HttpStatus.OK).body(monthCostsMap);
    }

    @GetMapping("/report-by-mail")
    public ResponseEntity setReportToUser(@AuthenticationPrincipal User user) {
        user = userService.getUserById(user.getId());
        monthReportActivity.sendToUser(user);
        return ResponseEntity.status(HttpStatus.OK).body(null);
    }

    private Map<String, Double> getMonthCostsTotalByCategory(List<MonthCosts> monthCostsByUser, Category category) {
        List<MonthCosts> monthCostsByCategory = monthCostsByUser.stream()
                .filter(monthCosts -> monthCosts.getCategory().equals(category))
                .sorted(Comparator.comparing(MonthCosts::getStartDate))
                .collect(Collectors.toList());
        if(monthCostsByCategory.size() == 0){
            return Collections.emptyMap();
        }
        Map<String, Double> monthCostsMap = monthCostsByUser.stream()
                .filter(monthCosts -> monthCosts.getAmountUSD() > 0)
                .filter(monthCosts -> monthCosts.getCategory().equals(category))
                .collect(Collectors.groupingBy(monthCosts -> monthCosts.getCategory().getName(),
                        Collectors.summingDouble(MonthCosts::getAmountUSD)));

        monthCostsMap.put(category.getName(), monthCostsMap.get(category.getName()));

        return monthCostsMap;
    }


    @GetMapping("/tables")
    public ResponseEntity<List<MonthCostsTableRecord>> getTableRecords(@AuthenticationPrincipal User user) {
        user = userService.getUserById(user.getId());
        LocalDate startDate = LocalDate.of(LocalDate.now().getYear(), LocalDate.now().getMonth(), 1);
        List<MonthCosts> monthCostsByUser = monthCostsService.findMonthCostsByUser(user);
        List<MonthCostsTableRecord> monthCostsTableRecordList = monthCostsByUser.stream()
                .filter(monthCosts -> monthCosts.getStartDate().compareTo(startDate) >= 0)
                .sorted(Comparator.comparing(MonthCosts::getStartDate))
                .map(MonthCostsTableRecord::fromMonthCosts)
                .collect(Collectors.toList());
        return ResponseEntity.status(HttpStatus.OK).body(monthCostsTableRecordList);
    }

    private List<Double> getAmountsByCategoryFromDate(Category category, LocalDate date,
                                                      List<MonthCosts> monthCostsList) {
        List<Double> amounts = new ArrayList<>();
        while (date.compareTo(LocalDate.now()) <= 0) {
            MonthCosts monthCosts = getMonthCostsByCategoryAndDate(category, date, monthCostsList);
            if (monthCosts == null) {
                amounts.add(0d);
            } else {
                amounts.add(monthCosts.getAmountUSD());
            }
            date = date.plusMonths(1);
        }
        return amounts;
    }

    private MonthCosts getMonthCostsByCategoryAndDate(Category category, LocalDate date,
                                                      List<MonthCosts> monthCostsList) {

        for (MonthCosts monthCosts: monthCostsList) {
            if (monthCosts.getStartDate().equals(date) && monthCosts.getCategory().equals(category)) {
                return monthCosts;
            }
        }
        return null;
    }

    @Autowired
    public void setMonthCostsService(MonthCostsService monthCostsService) {
        this.monthCostsService = monthCostsService;
    }

    @Autowired
    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    @Autowired
    public void setMonthReportActivity(MonthReportActivity monthReportActivity) {
        this.monthReportActivity = monthReportActivity;
    }
}

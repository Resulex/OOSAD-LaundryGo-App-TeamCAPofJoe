package com.laundrygo.service;

import com.laundrygo.db.AuditDao;
import com.laundrygo.db.CustomerDao;
import com.laundrygo.db.OrderDao;
import com.laundrygo.db.PaymentDao;
import com.laundrygo.db.ServiceTypeDao;
import com.laundrygo.db.SettingDao;
import com.laundrygo.db.UserDao;

/** Simple service locator shared by every view and dialog. */
public final class AppServices {

    private final UserDao users = new UserDao();
    private final CustomerDao customers = new CustomerDao();
    private final ServiceTypeDao serviceTypes = new ServiceTypeDao();
    private final OrderDao orders = new OrderDao();
    private final PaymentDao payments = new PaymentDao();
    private final AuditDao audit = new AuditDao();
    private final SettingDao settings = new SettingDao();

    private final PenaltyService penalty = new PenaltyService(settings);
    private final OrderService orderService = new OrderService(orders, payments, audit, penalty, customers, serviceTypes);
    private final AuthService auth = new AuthService(users, audit);
    private final SmsService sms = new SmsService();
    private final ReportService reports = new ReportService(orders, payments, audit);

    public UserDao users() {
        return users;
    }

    public CustomerDao customers() {
        return customers;
    }

    public ServiceTypeDao serviceTypes() {
        return serviceTypes;
    }

    public OrderDao orders() {
        return orders;
    }

    public PaymentDao payments() {
        return payments;
    }

    public AuditDao audit() {
        return audit;
    }

    public SettingDao settings() {
        return settings;
    }

    public PenaltyService penalty() {
        return penalty;
    }

    public OrderService orderService() {
        return orderService;
    }

    public AuthService auth() {
        return auth;
    }

    public SmsService sms() {
        return sms;
    }

    public ReportService reports() {
        return reports;
    }
}

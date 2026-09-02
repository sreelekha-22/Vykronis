package io.vykronis.demo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Notification findByNotificationId(String notificationId);

    boolean existsByNotificationId(String notificationId);
}
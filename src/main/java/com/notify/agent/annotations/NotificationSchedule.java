package com.notify.agent.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Optional: declare default schedules
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface NotificationSchedule {
    enum Kind { 
        IMMEDIATE, 
        DELAY, 
        CRON 
    }
    
    Kind kind();
    boolean eventEnabled() default true;
    String startTime() default "";
    int repeatCount() default -1; // REPEAT_INDEFINITELY = -1
    int repeatInterval() default 1;
    String repeatIntervalUnit() default "MINUTE";
    int[] daysOfWeek() default {};
    String startTimeOfDay() default "";
    String endTimeOfDay() default "";
}

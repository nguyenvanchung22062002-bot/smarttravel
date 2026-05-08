package com.smarttravel.service;

import com.smarttravel.entity.DailyCheckin;
import com.smarttravel.entity.User;
import com.smarttravel.entity.UserPoints;
import com.smarttravel.repository.DailyCheckinRepository;
import com.smarttravel.repository.UserPointsRepository;
import com.smarttravel.repository.UserRepository;
import com.smarttravel.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CheckinService {

    private final DailyCheckinRepository checkinRepo;
    private final UserPointsRepository   pointsRepo;
    private final UserRepository         userRepo;
    private final JwtUtils               jwtUtils;

    private static final int BASE_POINTS   = 20;
    private static final int WEEKLY_BONUS  = 100;
    private static final int WEEKEND_BONUS = 10;

    // GET /api/checkin/me
    @Transactional(readOnly = true)
    public Map<String, Object> getMyPoints(String authHeader) {
        User user = resolveUser(authHeader);
        UserPoints up = getOrCreatePoints(user);

        LocalDate today = LocalDate.now();
        boolean checkedToday = checkinRepo
                .findByUserIdAndCheckinDate(user.getId(), today)
                .isPresent();

        List<String> recentDates = checkinRepo
                .findByUserIdOrderByCheckinDateDesc(user.getId())
                .stream()
                .limit(30)
                .map(c -> c.getCheckinDate().toString())
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("totalPoints",       up.getTotalPoints());
        result.put("currentStreak",     up.getCurrentStreak());
        result.put("longestStreak",     up.getLongestStreak());
        result.put("totalCheckins",     up.getTotalCheckins());
        result.put("checkedToday",      checkedToday);
        result.put("recentDates",       recentDates);
        result.put("nextCheckinPoints", calcPoints(up.getCurrentStreak() + 1, today));
        return result;
    }


    // POST /api/checkin
    @Transactional
    public Map<String, Object> doCheckin(String authHeader) {
        User user = resolveUser(authHeader);
        LocalDate today = LocalDate.now();

        // Idempotency check
        if (checkinRepo.findByUserIdAndCheckinDate(user.getId(), today).isPresent()) {
            UserPoints up = getOrCreatePoints(user);
            Map<String, Object> r = new HashMap<>();
            r.put("alreadyChecked", true);
            r.put("message",        "Bạn đã check-in hôm nay rồi!");
            r.put("totalPoints",    up.getTotalPoints());
            r.put("currentStreak",  up.getCurrentStreak());
            r.put("pointsEarned",   0);
            return r;
        }

        UserPoints up = getOrCreatePoints(user);

        // Tính streak mới
        String lastDate   = up.getLastCheckinDate();
        LocalDate yesterday = today.minusDays(1);
        boolean streakBroken = (lastDate != null && !yesterday.toString().equals(lastDate));

        int newStreak;
        if (lastDate == null) {
            newStreak = 1;
        } else if (yesterday.toString().equals(lastDate)) {
            newStreak = up.getCurrentStreak() + 1;
        } else {
            newStreak = 1;
        }

        int    pointsEarned = calcPoints(newStreak, today);
        String bonusType    = getBonusType(newStreak, today);

        up.setCurrentStreak(newStreak);
        up.setLongestStreak(Math.max(up.getLongestStreak(), newStreak));
        up.setTotalPoints(up.getTotalPoints() + pointsEarned);
        up.setTotalCheckins(up.getTotalCheckins() + 1);
        up.setLastCheckinDate(today.toString());
        pointsRepo.save(up);

        DailyCheckin log = new DailyCheckin();
        log.setUser(user);
        log.setCheckinDate(today);
        log.setStreakAtCheckin(newStreak);
        log.setPointsAwarded(pointsEarned);
        log.setBonusType(bonusType);
        checkinRepo.save(log);

        Map<String, Object> result = new HashMap<>();
        result.put("alreadyChecked", false);
        result.put("message",        buildSuccessMsg(newStreak, pointsEarned, bonusType));
        result.put("pointsEarned",   pointsEarned);
        result.put("bonusType",      bonusType);
        result.put("totalPoints",    up.getTotalPoints());
        result.put("currentStreak",  newStreak);
        result.put("longestStreak",  up.getLongestStreak());
        result.put("streakBroken",   streakBroken);
        return result;
    }


    // PUBLIC helpers gọi từ service khác
    @Transactional
    public void awardBookingPoints(User user, int points) {
        UserPoints up = getOrCreatePoints(user);
        up.setTotalPoints(up.getTotalPoints() + points);
        pointsRepo.save(up);
    }

    @Transactional
    public void deductPoints(User user, int points) {
        UserPoints up = getOrCreatePoints(user);
        if (up.getTotalPoints() < points) {
            throw new IllegalArgumentException(
                    "Điểm không đủ! Hiện có " + up.getTotalPoints() + " điểm, cần " + points + " điểm.");
        }
        up.setTotalPoints(up.getTotalPoints() - points);
        pointsRepo.save(up);
    }

    // Private helpers
    private int calcPoints(int streak, LocalDate date) {
        int pts = BASE_POINTS;
        if (streak % 7 == 0) pts += WEEKLY_BONUS;
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) pts += WEEKEND_BONUS;
        return pts;
    }

    private String getBonusType(int streak, LocalDate date) {
        if (streak % 7 == 0) return "WEEKLY_STREAK";
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) return "WEEKEND";
        return "DAILY";
    }

    private String buildSuccessMsg(int streak, int points, String bonusType) {
        String base = "Check-in thành công! +" + points + " điểm";
        return switch (bonusType) {
            case "WEEKLY_STREAK" -> base + " 🔥 (Streak " + streak + " ngày — bonus tuần!)";
            case "WEEKEND"       -> base + " 🎉 (Bonus cuối tuần!)";
            default              -> base + " (Streak: " + streak + " ngày)";
        };
    }

    private UserPoints getOrCreatePoints(User user) {
        return pointsRepo.findByUserId(user.getId()).orElseGet(() -> {
            UserPoints up = new UserPoints();
            up.setUser(user);
            up.setTotalPoints(0);
            up.setCurrentStreak(0);
            up.setLongestStreak(0);
            up.setTotalCheckins(0);
            up.setLastCheckinDate(null);
            return pointsRepo.save(up);
        });
    }

    private User resolveUser(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer "))
            throw new IllegalArgumentException("Authorization header không hợp lệ");
        String email = jwtUtils.getEmailFromToken(authHeader.substring(7));
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user: " + email));
    }
}
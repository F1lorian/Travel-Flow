package com.sep.sep_backend.repos;

import com.sep.sep_backend.entities.Trip;
import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.entities.UserTrip;
import com.sep.sep_backend.entities.UserTripId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserTripRepository extends JpaRepository<UserTrip, UserTripId> {
  boolean existsByIdUserIdAndIdTripId(Long userId, Long tripId);

  void deleteByIdTripId(Long tripId);

  Optional<UserTrip> findByIdUserIdAndIdTripId(Long userId, Long tripId);

  Optional<UserTrip> findByUserAndTrip(User user, Trip trip);

  List<UserTrip> findByInvitedBy(User invitedBy);

  boolean existsByTrip(Trip trip);

  List<UserTrip> findByUser_IdAndStatus(Long userId, UserTrip.InviteStatus status);
}

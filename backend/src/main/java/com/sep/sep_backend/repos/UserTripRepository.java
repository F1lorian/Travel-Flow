package com.sep.sep_backend.repos;

import com.sep.sep_backend.entities.*;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserTripRepository extends JpaRepository<UserTrip, UserTripId> {
  boolean existsByIdUserIdAndIdTripId(Long userId, Long tripId);

  void deleteByIdTripId(Long tripId);

  Optional<UserTrip> findByIdUserIdAndIdTripId(Long userId, Long tripId);

  Optional<UserTrip> findByUserAndTrip(User user, Trip trip);

  List<UserTrip> findByInvitedBy(User invitedBy);

  boolean existsByTrip(Trip trip);

  List<UserTrip> findByUser_IdAndStatus(Long userId, UserTrip.InviteStatus status);

  List<UserTrip> findByTrip_IdAndStatus(long trip_id, UserTrip.InviteStatus status);
}

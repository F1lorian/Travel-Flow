package com.sep.sep_backend.repos;

import com.sep.sep_backend.Enitys.UserTrip;
import com.sep.sep_backend.Enitys.UserTripId;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserTripRepository extends JpaRepository<UserTrip, UserTripId> {
  boolean existsByIdUserIdAndIdTripId(Long userId, Long tripId);

  Optional<UserTrip> findByIdUserIdAndIdTripId(Long userId, Long tripId);
}

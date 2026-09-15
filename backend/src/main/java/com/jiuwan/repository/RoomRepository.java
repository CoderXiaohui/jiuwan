package com.jiuwan.repository;

import com.jiuwan.domain.Room;
import java.util.Set;

public interface RoomRepository {
  Room find(String code);

  boolean create(Room room);

  void save(Room room);

  Set<String> codes();
}

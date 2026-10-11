package storage;

import group.Group;

public interface GroupRespository {

    void save(Group group);

    Group findById(String groupId);
}

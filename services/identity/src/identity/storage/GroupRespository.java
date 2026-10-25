package identity.storage;

import identity.group.Group;

public interface GroupRespository {

    void save(Group group);

    Group findById(String groupId);
}

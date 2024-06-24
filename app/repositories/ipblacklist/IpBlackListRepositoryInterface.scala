package repositories.ipblacklist

import repositories.TypedCRUDRepositoryInterface

trait IpBlackListRepositoryInterface extends TypedCRUDRepositoryInterface[BlackListedIp, String] {}

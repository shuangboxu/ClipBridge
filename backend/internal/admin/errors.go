package admin

import "errors"

var (
	ErrNotFound                      = errors.New("record not found")
	ErrInvalidArgument               = errors.New("invalid argument")
	ErrPendingQuotaRequestExists     = errors.New("pending quota request already exists")
	ErrPendingBandwidthRequestExists = errors.New("pending bandwidth request already exists")
	ErrPendingAdminRequestExists     = errors.New("pending admin request already exists")
	ErrInvalidState                  = errors.New("invalid state")
	ErrLastAdmin                     = errors.New("last admin cannot be removed")
	ErrAlreadyAdmin                  = errors.New("user is already admin")
	ErrStorageQuotaExceeded          = errors.New("storage quota exceeded")
)

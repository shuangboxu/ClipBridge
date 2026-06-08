package admin

import "context"

type Repository interface {
	EnsureSystemSettings(ctx context.Context, seed SeedSettings) (SystemSettings, error)
	GetSystemSettings(ctx context.Context) (SystemSettings, error)
	CountUsers(ctx context.Context) (int, error)
	UpdateSystemSettings(ctx context.Context, input UpdateSettingsInput) (SystemSettings, error)
	GetAccountOverview(ctx context.Context, userID string) (AccountOverview, error)
	GetUserTransferPolicy(ctx context.Context, userID string) (UserTransferPolicy, error)
	CreateQuotaRequest(ctx context.Context, userID string, requestedQuotaBytes int64, reason string) (QuotaRequest, error)
	ListQuotaRequestsByUser(ctx context.Context, userID, status string) ([]QuotaRequest, error)
	CreateBandwidthRequest(ctx context.Context, userID string, requestedUploadKbps, requestedDownloadKbps int, reason string) (BandwidthRequest, error)
	ListBandwidthRequestsByUser(ctx context.Context, userID, status string) ([]BandwidthRequest, error)
	CreateAdminRequest(ctx context.Context, userID, reason string) (AdminRequest, error)
	ListAdminRequestsByUser(ctx context.Context, userID, status string) ([]AdminRequest, error)
	ListUsers(ctx context.Context) ([]UserSummary, error)
	UpdateUser(ctx context.Context, userID string, input UpdateUserInput) (UserSummary, error)
	DeleteUser(ctx context.Context, userID string) (DeleteUserResult, error)
	ListQuotaRequestsForAdmin(ctx context.Context, status string) ([]QuotaRequest, error)
	ApproveQuotaRequest(ctx context.Context, requestID, reviewerID string, input ApproveQuotaRequestInput) (QuotaRequest, error)
	RejectQuotaRequest(ctx context.Context, requestID, reviewerID string, input RejectRequestInput) (QuotaRequest, error)
	ListBandwidthRequestsForAdmin(ctx context.Context, status string) ([]BandwidthRequest, error)
	ApproveBandwidthRequest(ctx context.Context, requestID, reviewerID string, input ApproveBandwidthRequestInput) (BandwidthRequest, error)
	RejectBandwidthRequest(ctx context.Context, requestID, reviewerID string, input RejectRequestInput) (BandwidthRequest, error)
	ListAdminRequestsForAdmin(ctx context.Context, status string) ([]AdminRequest, error)
	ApproveAdminRequest(ctx context.Context, requestID, reviewerID string, input ApproveAdminRequestInput) (AdminRequest, error)
	RejectAdminRequest(ctx context.Context, requestID, reviewerID string, input RejectRequestInput) (AdminRequest, error)
}

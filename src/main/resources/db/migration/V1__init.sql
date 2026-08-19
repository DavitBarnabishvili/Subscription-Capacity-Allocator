CREATE TABLE subscription_optimization_run (
                                               id                      UUID PRIMARY KEY,
                                               max_capacity            NUMERIC(19,4) NOT NULL,
                                               total_requested_amount  NUMERIC(19,4) NOT NULL,
                                               total_fee_revenue       NUMERIC(19,4) NOT NULL,
                                               created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE subscription_request (
                                      id                BIGSERIAL PRIMARY KEY,
                                      run_id            UUID NOT NULL REFERENCES subscription_optimization_run(id) ON DELETE CASCADE,
                                      investor_name     VARCHAR(255) NOT NULL,
                                      requested_amount  NUMERIC(19,4) NOT NULL,
                                      fee_revenue       NUMERIC(19,4) NOT NULL,
                                      accepted          BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_subscription_request_run_id ON subscription_request (run_id);
CREATE INDEX idx_subscription_run_created_at ON subscription_optimization_run (created_at DESC);
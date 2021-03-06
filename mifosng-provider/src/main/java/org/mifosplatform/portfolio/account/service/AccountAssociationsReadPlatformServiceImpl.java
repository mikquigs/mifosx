package org.mifosplatform.portfolio.account.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.mifosplatform.infrastructure.core.domain.JdbcSupport;
import org.mifosplatform.infrastructure.core.service.RoutingDataSource;
import org.mifosplatform.portfolio.account.data.AccountAssociationsData;
import org.mifosplatform.portfolio.account.data.PortfolioAccountData;
import org.mifosplatform.portfolio.loanaccount.domain.LoanStatus;
import org.mifosplatform.portfolio.savings.domain.SavingsAccountStatusType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
public class AccountAssociationsReadPlatformServiceImpl implements AccountAssociationsReadPlatformService {

    private final static Logger logger = LoggerFactory.getLogger(AccountAssociationsReadPlatformServiceImpl.class);
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public AccountAssociationsReadPlatformServiceImpl(final RoutingDataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public PortfolioAccountData retriveLoanAssociation(final Long loanId) {
        PortfolioAccountData linkedAccount = null;
        final AccountAssociationsMapper mapper = new AccountAssociationsMapper();
        final String sql = "select " + mapper.schema() + " where aa.loan_account_id = ? ";
        try {
            final AccountAssociationsData accountAssociationsData = this.jdbcTemplate.queryForObject(sql, mapper, loanId);
            if (accountAssociationsData != null) {
                linkedAccount = accountAssociationsData.linkedAccount();
            }
        } catch (final EmptyResultDataAccessException e) {
            logger.debug("Linking account is not configured");
        }
        return linkedAccount;
    }
    
    @Override
    public PortfolioAccountData retriveSavingsAssociation(final Long savingsId) {
        PortfolioAccountData linkedAccount = null;
        final AccountAssociationsMapper mapper = new AccountAssociationsMapper();
        final String sql = "select " + mapper.schema() + " where aa.savings_account_id = ? ";
        try {
            final AccountAssociationsData accountAssociationsData = this.jdbcTemplate.queryForObject(sql, mapper, savingsId);
            if (accountAssociationsData != null) {
                linkedAccount = accountAssociationsData.linkedAccount();
            }
        } catch (final EmptyResultDataAccessException e) {
            logger.debug("Linking account is not configured");
        }
        return linkedAccount;
    }

    @Override
    public boolean isLinkedWithAnyActiveAccount(final Long savingsId) {
        boolean hasActiveAccount = false;
        final String sql = "select loanAccount.loan_status_id as status from m_portfolio_account_associations aa "
                + "left join m_loan loanAccount on loanAccount.id = aa.loan_account_id " + "where aa.linked_savings_account_id = ?";

        final List<Integer> statusList = this.jdbcTemplate.queryForList(sql, Integer.class, savingsId);
        for (final Integer status : statusList) {
            final LoanStatus loanStatus = LoanStatus.fromInt(status);
            if (loanStatus.isActiveOrAwaitingApprovalOrDisbursal() || loanStatus.isUnderTransfer()) { return true; }
        }

        final String savsql = "select savingAccount.status_enum as status from m_portfolio_account_associations aa "
                + "left join m_savings_account savingAccount on savingAccount.id = aa.savings_account_id "
                + "where aa.linked_savings_account_id = ?";

        final List<Integer> savstatusList = this.jdbcTemplate.queryForList(savsql, Integer.class, savingsId);
        for (final Integer status : savstatusList) {
            final SavingsAccountStatusType saveStatus = SavingsAccountStatusType.fromInt(status);
            if (saveStatus.isActiveOrAwaitingApprovalOrDisbursal() || saveStatus.isUnderTransfer()) { return true; }
        }

        return hasActiveAccount;
    }

    private static final class AccountAssociationsMapper implements RowMapper<AccountAssociationsData> {

        private final String schemaSql;

        public AccountAssociationsMapper() {
            final StringBuilder sqlBuilder = new StringBuilder();
            sqlBuilder.append("aa.id as id,");
            // sqlBuilder.append("savingsAccount.id as savingsAccountId, savingsAccount.account_no as savingsAccountNo,");
            sqlBuilder.append("loanAccount.id as loanAccountId, loanAccount.account_no as loanAccountNo,");
            // sqlBuilder.append("linkLoanAccount.id as linkLoanAccountId, linkLoanAccount.account_no as linkLoanAccountNo, ");
            sqlBuilder.append("linkSavingsAccount.id as linkSavingsAccountId, linkSavingsAccount.account_no as linkSavingsAccountNo ");
            sqlBuilder.append("from m_portfolio_account_associations aa ");
            // sqlBuilder.append("left join m_savings_account savingsAccount on savingsAccount.id = aa.savings_account_id ");
            sqlBuilder.append("left join m_loan loanAccount on loanAccount.id = aa.loan_account_id ");
            sqlBuilder.append("left join m_savings_account linkSavingsAccount on linkSavingsAccount.id = aa.linked_savings_account_id ");
            // sqlBuilder.append("left join m_loan linkLoanAccount on linkLoanAccount.id = aa.linked_loan_account_id ");
            this.schemaSql = sqlBuilder.toString();
        }

        public String schema() {
            return this.schemaSql;
        }

        @Override
        public AccountAssociationsData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum) throws SQLException {

            final Long id = rs.getLong("id");
            // final Long savingsAccountId = JdbcSupport.getLong(rs,
            // "savingsAccountId");
            // final String savingsAccountNo = rs.getString("savingsAccountNo");
            final Long loanAccountId = JdbcSupport.getLong(rs, "loanAccountId");
            final String loanAccountNo = rs.getString("loanAccountNo");
            final PortfolioAccountData account = PortfolioAccountData.lookup(loanAccountId, loanAccountNo);
            /*
             * if (savingsAccountId != null) { account =
             * PortfolioAccountData.lookup(savingsAccountId, savingsAccountNo);
             * } else if (loanAccountId != null) { account =
             * PortfolioAccountData.lookup(loanAccountId, loanAccountNo); }
             */
            final Long linkSavingsAccountId = JdbcSupport.getLong(rs, "linkSavingsAccountId");
            final String linkSavingsAccountNo = rs.getString("linkSavingsAccountNo");
            // final Long linkLoanAccountId = JdbcSupport.getLong(rs,
            // "linkLoanAccountId");
            // final String linkLoanAccountNo =
            // rs.getString("linkLoanAccountNo");
            final PortfolioAccountData linkedAccount = PortfolioAccountData.lookup(linkSavingsAccountId, linkSavingsAccountNo);
            /*
             * if (linkSavingsAccountId != null) { linkedAccount =
             * PortfolioAccountData.lookup(linkSavingsAccountId,
             * linkSavingsAccountNo); } else if (linkLoanAccountId != null) {
             * linkedAccount = PortfolioAccountData.lookup(linkLoanAccountId,
             * linkLoanAccountNo); }
             */

            return new AccountAssociationsData(id, account, linkedAccount);
        }

    }
}

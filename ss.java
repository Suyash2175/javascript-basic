package com.txbdy.itr.service.impl;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TimeZone;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.xml.transform.TransformerException;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.fop.apps.FOPException;
import org.bson.Document;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.XML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.WebApplicationContext;

import com.amazonaws.xray.spring.aop.XRayEnabled;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taxbuddy.itr.domain.Allowance;
import com.taxbuddy.itr.domain.Assessee;
import com.taxbuddy.itr.domain.Assessment;
import com.taxbuddy.itr.domain.Business;
import com.taxbuddy.itr.domain.Depreciation;
import com.taxbuddy.itr.domain.Donation;
import com.taxbuddy.itr.domain.Employer;
import com.taxbuddy.itr.domain.FinancialParticulars;
import com.taxbuddy.itr.domain.Income;
import com.taxbuddy.itr.domain.Incomes;
import com.taxbuddy.itr.domain.OnSalary;
import com.taxbuddy.itr.domain.OtherThanSalary16A;
import com.taxbuddy.itr.domain.OtherThanSalary26QB;
import com.taxbuddy.itr.domain.OtherThanTDSTCS;
import com.taxbuddy.itr.domain.Person;
import com.taxbuddy.itr.domain.PresumptiveIncomes;
import com.taxbuddy.itr.domain.SalaryDeductions;
import com.taxbuddy.itr.domain.TaxPaid;
import com.taxbuddy.itr.domain.TaxSummary;
import com.taxbuddy.itr.domain.Tcs;
import com.txbdy.itr.config.ApplicationProperties;
import com.txbdy.itr.config.Constants;
import com.txbdy.itr.domain.DividendIncome;
import com.txbdy.itr.domain.DonationType;
import com.txbdy.itr.domain.Family;
import com.txbdy.itr.domain.Itr;
import com.txbdy.itr.domain.ItrConstants;
import com.txbdy.itr.domain.ItrJsonConstants;
import com.txbdy.itr.domain.NatureOfBusinessType;
import com.txbdy.itr.domain.NewTaxRegime;
import com.txbdy.itr.domain.Summary;
import com.txbdy.itr.domain.SystemFlag;
import com.txbdy.itr.domain.UserProfile;
import com.txbdy.itr.domain.dto.AgentDetailsDTO;
import com.txbdy.itr.domain.dto.CapitalGainIncome;
import com.txbdy.itr.domain.dto.DepreciationDetailsDTO;
import com.txbdy.itr.domain.dto.ItrFlowSummaryDTO;
import com.txbdy.itr.domain.dto.LongTermCapitalGainAt10Percent;
import com.txbdy.itr.domain.dto.LongTermCapitalGainAt20Percent;
import com.txbdy.itr.domain.dto.ShortTermCapitalGain;
import com.txbdy.itr.domain.dto.ShortTermCapitalGainAt15Percent;
import com.txbdy.itr.repository.ItrRepository;
import com.txbdy.itr.repository.SummaryRepository;
import com.txbdy.itr.repository.UserProfileRepository;
import com.txbdy.itr.service.AgentAssignmentDetailsService;
import com.txbdy.itr.service.CalculatorService;
import com.txbdy.itr.service.ChatService;
import com.txbdy.itr.service.InvoiceServiceV1;
import com.txbdy.itr.service.MasterCollectionService;
import com.txbdy.itr.service.SequenceException;
import com.txbdy.itr.service.SequenceService;
import com.txbdy.itr.service.SummaryService;
import com.txbdy.itr.web.rest.EfillingResource;
import com.txbdy.itr.web.rest.errors.BadRequestAlertException;
import com.txbdy.itr.web.rest.errors.DataNotFoundExpection;
import com.txbdy.itr.web.rest.util.Utils;

import kong.unirest.core.Unirest;

@Service
@XRayEnabled
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class SummaryServiceImpl implements SummaryService {

	private final Logger log = LoggerFactory.getLogger(SummaryServiceImpl.class);

	@Autowired
	private SummaryRepository summaryRepository;
	@Autowired
	private MasterCollectionService masterCollectionService;
	@Autowired
	private SequenceService sequenceService;
	@Autowired
	private UserProfileRepository userProfileRepository;
	@Autowired
	private ApplicationProperties applicationProperties;
	@Autowired
	private Environment environment;
	@Autowired
	private ItrRepository itrRepository;
	@Autowired
	private AgentAssignmentDetailsService agentAssignmentDetailsService;
	@Autowired
	private CalculatorService calculatorService;
	@Autowired
	private InvoiceServiceV1 invoiceServiceV1;
	@Autowired
	private MongoTemplate mongoTemplate;

	@Autowired
	private EfillingResource efillingResource;
	
	@Autowired
	private ChatService chatService;

	@Override
	public Summary saveAndUpdateItrSummary(Summary summary) {

		Summary summaryData = getSummary(summary.getSummaryId());

		String[] env = environment.getActiveProfiles();
		boolean isProd = false;
		if (env[env.length - 1].equalsIgnoreCase("prod"))
			isProd = true;

		if (summaryData == null) {

			List<Employer> employerList = summary.getAssesse().getEmployers();

			if (!employerList.isEmpty()) {
				for (Employer e : employerList) {

					List<SalaryDeductions> salaryDeductionList = e.getDeductions();
					if (!salaryDeductionList.isEmpty()) {
						for (SalaryDeductions s : salaryDeductionList) {
							if (s.getDeductionType().equals("PROFESSIONAL_TAX"))
								e.setTotalPTDuctionsExemptIncome(s.getExemptAmount());
							else if (s.getDeductionType().equals("ENTERTAINMENT_ALLOW"))
								e.setTotalETDuctionsExemptIncome(s.getExemptAmount());
						}
					}
				}

			}

			long summaryId = 0;
			try {
				summaryId = sequenceService.getNextSequenceId("summary_id");
				summary.setSummaryId(summaryId);
			} catch (SequenceException e) {
				e.printStackTrace();
			}

			if (isProd && summary.getUs80ccd1b() < 50000)
				addSummaryDataToGoogleSheet(summary);

			return summaryRepository.insert(summary);

		}

		if (isProd && summary.getUs80ccd1b() < 50000)
			addSummaryDataToGoogleSheet(summary);

		return summaryRepository.save(summary);

	}

	@Override
	public byte[] generateSummaryPdf(long summaryId) {
		try {

			Summary summary = getSummary(summaryId);
			String xml = "";
			String xsl = "";
			if (summary != null) {

				String summaryPdfXml = generatSummaryPdfXML(summary);

				Pattern pattern = Pattern
						.compile("[^\\u0009\\u000A\\u000D\\u0020-\\uD7FF\\uE000-\\uFFFD\\u10000-\\u10FFF]+");
				summaryPdfXml = pattern.matcher(summaryPdfXml).replaceAll("");
				summaryPdfXml = summaryPdfXml.replace("&", "&amp;");
				if (summary.getAssesse() != null && summary.getAssesse().getItrType().equals("1")) {

					xml = "<?xml version = \"1.0\"?>\r\n"
							+ "<?xml-stylesheet type = \"text/xsl\" href = \"ITR1Summary.xsl\"?>\r\n" + "<summary>\r\n"
							+ summaryPdfXml + "</summary>";

					xsl = IOUtils.toString(getClass().getResourceAsStream("/XSLFiles/ITR1Summary.xsl"),
							Charset.defaultCharset());

				} else if (summary.getAssesse() != null && summary.getAssesse().getItrType().equals("4")) {

					xml = "<?xml version = \"1.0\"?>\r\n"
							+ "<?xml-stylesheet type = \"text/xsl\" href = \"ITR4Summary.xsl\"?>\r\n" + "<summary>\r\n"
							+ summaryPdfXml + "</summary>";

					xsl = IOUtils.toString(getClass().getResourceAsStream("/XSLFiles/ITR4Summary.xsl"),
							Charset.defaultCharset());

				} else if (summary.getAssesse() != null && summary.getAssesse().getItrType().equals("2")) {
					xml = "<?xml version = \"1.0\"?>\r\n"
							+ "<?xml-stylesheet type = \"text/xsl\" href = \"ITR2Summary.xsl\"?>\r\n" + "<summary>\r\n"
							+ summaryPdfXml + "</summary>";

					xsl = IOUtils.toString(getClass().getResourceAsStream("/XSLFiles/ITR2Summary.xsl"),
							Charset.defaultCharset());
				} else if (summary.getAssesse() != null && summary.getAssesse().getItrType().equals("3")) {
					xml = "<?xml version = \"1.0\"?>\r\n"
							+ "<?xml-stylesheet type = \"text/xsl\" href = \"ITR3Summary.xsl\"?>\r\n" + "<summary>\r\n"
							+ summaryPdfXml + "</summary>";

					xsl = IOUtils.toString(getClass().getResourceAsStream("/XSLFiles/ITR3Summary.xsl"),
							Charset.defaultCharset());
				}
				if (!xsl.isEmpty() && !xml.isEmpty())
					return Utils.generatePdf(xml, xsl);
			}

		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
		return null;
	}

	private String generatSummaryPdfXML(Summary summary) {

		StringBuilder summaryPdfXml = null;

		try {

			ObjectMapper obj = new ObjectMapper();

			Assessee assessee = summary.getAssesse();

			if (assessee.getRegime() != null && !assessee.getRegime().isEmpty()) {
				if (assessee.getRegime().equals("N"))
					assessee.setRegime("Old Tax Regime");
				else if (assessee.getRegime().equals("Y"))
					assessee.setRegime("New Tax Regime");
			}

			assessee.setContactNumber("XXXXXXXXXX");

			if (assessee.getEmployerCategory() != null && !assessee.getEmployerCategory().isEmpty()) {
				if (assessee.getEmployerCategory().equals("SGOV"))
					assessee.setEmployerCategory("State Government");
				else if (assessee.getEmployerCategory().equals("CGOV"))
					assessee.setEmployerCategory("Central Government");
				else if (assessee.getEmployerCategory().equals("PSU"))
					assessee.setEmployerCategory("Public Sector Unit");
				else if (assessee.getEmployerCategory().equals("OTH"))
					assessee.setEmployerCategory("Other-Private");
				else if (assessee.getEmployerCategory().equals("PE"))
					assessee.setEmployerCategory("Pensioners");
				else if (assessee.getEmployerCategory().equals("NA"))
					assessee.setEmployerCategory("Not-Applicable");
			}

			List<Donation> donations = assessee.getDonations();

			boolean hasPropertyType = false;

			for (Donation donation : donations) {
				if (donation.getSchemeCode() != null && !donation.getSchemeCode().isEmpty()) {
					hasPropertyType = true;
					if (donation.getSchemeCode().equals("GOVT_APPRVD_FAMLY_PLNG"))
						donation.setSchemeCode("100% Deduction subject to qualifying limit");
					else if (donation.getSchemeCode().equals("FND_SEC80G"))
						donation.setSchemeCode("50% Deduction subject to qualifying limit");
					else if (donation.getSchemeCode().equals("NAT_DEF_FUND_CEN_GOVT"))
						donation.setSchemeCode("100% Deduction without qualifying limit");
					else if (donation.getSchemeCode().equals("JN_MEM_FND"))
						donation.setSchemeCode("50% Deduction without qualifying limit");
				} else
					donation.setSchemeCode("");
			}

			assessee.setDonations(donations);

			summary.setAssesse(assessee);

			String json = obj.writeValueAsString(summary);

			summaryPdfXml = new StringBuilder(XML.toString(new JSONObject(json)));

			SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");

			dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));

			if (assessee != null && !assessee.getFamily().isEmpty()) {
				String dateOfBirth = dateFormat
						.format(Date.from(Instant.parse(assessee.getFamily().get(0).getDateOfBirth())));

				summaryPdfXml.append("<dateOfBirth>" + dateOfBirth + "</dateOfBirth>");
			}

			summaryPdfXml.append("<hasPropertyType>" + hasPropertyType + "</hasPropertyType>");

			if (assessee.geteFillingDate() != null)
				summaryPdfXml
						.append("<dateOfFiling>" + dateFormat.format(assessee.geteFillingDate()) + "</dateOfFiling>");
			NewTaxRegime newTaxRegime = summary.getNewTaxRegime();
			if (newTaxRegime != null) {
				BigDecimal zero = BigDecimal.valueOf(0);
				if (newTaxRegime.getSalary().equals(zero) && newTaxRegime.getHousePropertyIncome().equals(zero)
						&& newTaxRegime.getOtherIncome().equals(zero) && newTaxRegime.getGrossTotalIncome().equals(zero)
						&& newTaxRegime.getTotalDeduction().equals(zero)
						&& newTaxRegime.getTotalIncomeAfterDeductionIncludeSR().equals(zero)
						&& newTaxRegime.getTaxOnTotalIncome().equals(zero)
						&& newTaxRegime.getForRebate87Tax().equals(zero)
						&& newTaxRegime.getTaxAfterRebate().equals(zero) && newTaxRegime.getTotalTax().equals(zero)
						&& newTaxRegime.getTaxReliefUnder89().equals(zero) && newTaxRegime.getS234A().equals(zero)
						&& newTaxRegime.getS234B().equals(zero) && newTaxRegime.getS234C().equals(zero)
						&& newTaxRegime.getS234F().equals(zero) && newTaxRegime.getInterestAndFeesPayable().equals(zero)
						&& newTaxRegime.getTotalTaxesPaid().equals(zero) && newTaxRegime.getTaxRefund().equals(zero)
						&& newTaxRegime.getTaxpayable().equals(zero)
						&& newTaxRegime.getAgrigateLiability().equals(zero))
					summaryPdfXml.append("<isNewTaxRegime>" + false + "</isNewTaxRegime>");
				else
					summaryPdfXml.append("<isNewTaxRegime>" + true + "</isNewTaxRegime>");
			}

			TaxPaid taxPaid = null;

			if (assessee != null)
				taxPaid = assessee.getTaxPaid();

			if (taxPaid != null) {

				List<OnSalary> tdsOnSalaryList = taxPaid.getOnSalary();
				List<OtherThanSalary16A> tdsOtherThanSalaryList = taxPaid.getOtherThanSalary16A();
				List<OtherThanSalary26QB> tdsOtherThanSalary26QBList = taxPaid.getOtherThanSalary26QB();
				List<Tcs> tcsList = taxPaid.getTcs();
				List<OtherThanTDSTCS> otherThanTDSTCSList = taxPaid.getOtherThanTDSTCS();

				int tdsOnSalary = 0;
				int tdsOtherThanSalary = 0;
				int tdsOnSaleOfProperty26QB = 0;
				int tcs = 0;
				int advanceTaxSelfAssessmentTax = 0;

				if (!tdsOnSalaryList.isEmpty()) {
					for (OnSalary t : tdsOnSalaryList)
						tdsOnSalary = tdsOnSalary + t.getTotalTdsDeposited().intValue();
				}

				if (!tdsOtherThanSalaryList.isEmpty()) {
					for (OtherThanSalary16A t : tdsOtherThanSalaryList)
						tdsOtherThanSalary = tdsOtherThanSalary + t.getTotalTdsDeposited().intValue();
				}

				if (!tdsOtherThanSalary26QBList.isEmpty()) {
					for (OtherThanSalary26QB t : tdsOtherThanSalary26QBList)
						tdsOnSaleOfProperty26QB = tdsOnSaleOfProperty26QB + t.getTotalTdsDeposited().intValue();
				}

				if (!tcsList.isEmpty()) {
					for (Tcs t : tcsList)
						tcs = tcs + t.getTotalTcsDeposited().intValue();
				}

				if (!otherThanTDSTCSList.isEmpty()) {
					summaryPdfXml.append("<otherThanTDSTCSDatesOfDeposit>");
					for (OtherThanTDSTCS t : otherThanTDSTCSList) {

						advanceTaxSelfAssessmentTax = advanceTaxSelfAssessmentTax + t.getTotalTax().intValue();

						summaryPdfXml.append("<otherThanTDSTCSDateOfDeposit>" + dateFormat.format(t.getDateOfDeposit())
								+ "</otherThanTDSTCSDateOfDeposit>");
					}
					summaryPdfXml.append("</otherThanTDSTCSDatesOfDeposit>");
				}

				summaryPdfXml.append("<totalTdsOnSalary>" + tdsOnSalary + "</totalTdsOnSalary>");
				summaryPdfXml
						.append("<totalTdsOnOtherThanSalary>" + tdsOtherThanSalary + "</totalTdsOnOtherThanSalary>");
				summaryPdfXml.append(
						"<totalTdsSaleOfProperty26QB>" + tdsOnSaleOfProperty26QB + "</totalTdsSaleOfProperty26QB>");
				summaryPdfXml.append("<totalTaxCollectedAtSources>" + tcs + "</totalTaxCollectedAtSources>");
				summaryPdfXml.append("<totalAdvanceTax>" + advanceTaxSelfAssessmentTax + "</totalAdvanceTax>");

			}

			List<Employer> employerList = assessee.getEmployers();

			if (!employerList.isEmpty()) {

				int houseRentAllowance = 0;
				int leaveTravelAllowance = 0;
				int otherAllowance = 0;

				int standardDeduction = 0;
				int entertainmentAllowance = 0;
				int professionalTax = 0;

				int salaryDeduction = 0;

				for (Employer e : employerList) {

					List<Allowance> allowanceList = e.getAllowance();

					if (!allowanceList.isEmpty()) {
						summaryPdfXml.append("<exemptAllowances>");

						for (Allowance a : allowanceList) {

							if (a.getAllowanceType().equals("HOUSE_RENT"))
								summaryPdfXml.append("<houseRentAllowance>" + a.getExemptAmount().intValue()
										+ "</houseRentAllowance>");
							else if (a.getAllowanceType().equals("LTA"))
								summaryPdfXml.append("<leaveTravelAllowance>" + a.getExemptAmount().intValue()
										+ "</leaveTravelAllowance>");
							else if (a.getAllowanceType().equals("ANY_OTHER"))
								summaryPdfXml.append(
										"<otherAllowance>" + a.getExemptAmount().intValue() + "</otherAllowance>");
						}

						if (!summaryPdfXml.toString().contains("houseRentAllowance"))
							summaryPdfXml.append("<houseRentAllowance>" + houseRentAllowance + "</houseRentAllowance>");
						if (!summaryPdfXml.toString().contains("leaveTravelAllowance"))
							summaryPdfXml.append(
									"<leaveTravelAllowance>" + leaveTravelAllowance + "</leaveTravelAllowance>");
						if (!summaryPdfXml.toString().contains("otherAllowance"))
							summaryPdfXml.append("<otherAllowance>" + otherAllowance + "</otherAllowance>");

						summaryPdfXml.append("</exemptAllowances>");
					} else {
						summaryPdfXml.append("<exemptAllowances>");
						summaryPdfXml.append("<houseRentAllowance>" + houseRentAllowance + "</houseRentAllowance>");
						summaryPdfXml
								.append("<leaveTravelAllowance>" + leaveTravelAllowance + "</leaveTravelAllowance>");
						summaryPdfXml.append("<otherAllowance>" + otherAllowance + "</otherAllowance>");
						summaryPdfXml.append("</exemptAllowances>");
					}

					summaryPdfXml.append("<salaryDeductions>");

					standardDeduction = e.getStandardDeduction().intValue();
					professionalTax = e.getTotalPTDuctionsExemptIncome().intValue();
					entertainmentAllowance = e.getTotalETDuctionsExemptIncome().intValue();
					salaryDeduction = standardDeduction + entertainmentAllowance + professionalTax;

					summaryPdfXml.append("<standardDeduction>" + standardDeduction + "</standardDeduction>");
					summaryPdfXml
							.append("<entertainmentAllowance>" + entertainmentAllowance + "</entertainmentAllowance>");
					summaryPdfXml.append("<professionalTax>" + professionalTax + "</professionalTax>");
					summaryPdfXml.append("<salaryDeduction>" + salaryDeduction + "</salaryDeduction>");

					summaryPdfXml.append("</salaryDeductions>");

				}

			}

			List<Income> incomeList = assessee.getIncomes();

			if (!incomeList.isEmpty()) {

				int savingInterest = 0;
				int fdRdInterest = 0;
				int taxRefundInterest = 0;
				int otherInterest = 0;
				int totalExcemptIncome = 0;
				int agricultureIncome = 0;
				int dividendIncome = 0;
				int familyPension = 0;

				for (Income i : incomeList) {
					if (i.getIncomeType().equals("SAVING_INTEREST"))
						savingInterest = i.getAmount().intValue();
					else if (i.getIncomeType().equals("FD_RD_INTEREST"))
						fdRdInterest = i.getAmount().intValue();
					else if (i.getIncomeType().equals("TAX_REFUND_INTEREST"))
						taxRefundInterest = i.getAmount().intValue();
					else if (i.getIncomeType().equals("ANY_OTHER"))
						otherInterest = i.getAmount().intValue();
					else if (i.getIncomeType().equals("GIFT_NONTAXABLE"))
						totalExcemptIncome = i.getAmount().intValue();
					else if (i.getIncomeType().equals("AGRICULTURE_INCOME"))
						agricultureIncome = i.getAmount().intValue();
					else if (i.getIncomeType().equals("DIVIDEND"))
						dividendIncome = i.getAmount().intValue();
					else if (i.getIncomeType().equals("FAMILY_PENSION"))
						familyPension = i.getAmount().intValue();

				}
				summaryPdfXml.append("<otherIncome>");

				summaryPdfXml.append("<savingInterest>" + savingInterest + "</savingInterest>");
				summaryPdfXml.append("<fdRdInterest>" + fdRdInterest + "</fdRdInterest>");
				summaryPdfXml.append("<taxRefundInterest>" + taxRefundInterest + "</taxRefundInterest>");
				summaryPdfXml.append("<otherInterest>" + otherInterest + "</otherInterest>");
				summaryPdfXml.append("<totalExcemptIncome>" + totalExcemptIncome + "</totalExcemptIncome>");
				summaryPdfXml.append("<agricultureIncome>" + agricultureIncome + "</agricultureIncome>");
				summaryPdfXml.append("<dividendIncome>" + dividendIncome + "</dividendIncome>");
				summaryPdfXml.append("<familyPension>" + familyPension + "</familyPension>");
				summaryPdfXml.append("</otherIncome>");
			}

			List<Donation> donationList = assessee.getDonations();

			int totalAmountInCash = 0;

			int totalAmountOtherThanCash = 0;

			if (!donationList.isEmpty()) {

				for (Donation d : donationList) {

					totalAmountInCash = totalAmountInCash + d.getAmountInCash().intValue();

					totalAmountOtherThanCash = totalAmountOtherThanCash + d.getAmountOtherThanCash().intValue();

				}
				summaryPdfXml.append("<donationTotal>");

				summaryPdfXml.append("<totalAmountInCash>" + totalAmountInCash + "</totalAmountInCash>");

				summaryPdfXml.append(
						"<totalAmountOtherThanCash>" + totalAmountOtherThanCash + "</totalAmountOtherThanCash>");

				summaryPdfXml.append("</donationTotal>");

			}

			summaryPdfXml.append(
					"<sumOfUs80cUs80cccUs80ccc1>" + (summary.getUs80c() + summary.getUs80ccc() + summary.getUs80ccc1())
							+ "</sumOfUs80cUs80cccUs80ccc1>");

			summaryPdfXml.append(
					"<sumOfUs80ggaUs80ggc>" + (summary.getUs80gga() + summary.getUs80ggc()) + "</sumOfUs80ggaUs80ggc>");

			TaxSummary taxSummary = summary.getTaxSummary();

			boolean isTaxPayable = true;
			if ((taxSummary != null && taxSummary.getTaxRefund().intValue() != 0)
					|| (newTaxRegime != null && newTaxRegime.getTaxRefund().intValue() != 0))
				isTaxPayable = false;
			summaryPdfXml.append("<isTaxPayable>" + isTaxPayable + "</isTaxPayable>");

			boolean showTaxPayableContent = false;

			if ((taxSummary != null && taxSummary.getTaxpayable().intValue() > 0)
					|| (newTaxRegime != null && newTaxRegime.getTaxpayable().intValue() > 0))
				showTaxPayableContent = true;

			summaryPdfXml.append("<showTaxPayableContent>" + showTaxPayableContent + "</showTaxPayableContent>");

			if (assessee.getItrType().equals("4")) {

				Business business = assessee.getBusiness();
				int totalBusinessGrossReceipts = 0;
				int totalBusinessPresumptiveIncome = 0;

				int totalProfessionGrossReceipts = 0;
				int totalProfessionPresumptiveIncome = 0;

				String businessName = "";
				String professionName = "";
				String natureOfBusiness = "";
				String natureOfProfession = "";

				if (business != null) {

					List<PresumptiveIncomes> presumptiveIncomesList = business.getPresumptiveIncomes();
					if (!presumptiveIncomesList.isEmpty()) {

						for (PresumptiveIncomes b : presumptiveIncomesList) {
							if (b.getBusinessType().equals("BUSINESS")) {
								businessName = b.getTradeName();

								NatureOfBusinessType natureOfBusinessType = masterCollectionService
										.findbyBusinessCode(b.getNatureOfBusiness());

								if (natureOfBusinessType != null)
									natureOfBusiness = natureOfBusinessType.getLabel();

								List<Incomes> incomesList = b.getIncomes();
								if (!incomesList.isEmpty()) {

									for (Incomes i : incomesList) {
										totalBusinessGrossReceipts = totalBusinessGrossReceipts
												+ i.getReceipts().intValue();
										totalBusinessPresumptiveIncome = totalBusinessPresumptiveIncome
												+ i.getPresumptiveIncome().intValue();
									}
								}
							} else if (b.getBusinessType().equals("PROFESSIONAL")) {
								professionName = b.getTradeName();

								NatureOfBusinessType natureOfBusinessType = masterCollectionService
										.findbyBusinessCode(b.getNatureOfBusiness());

								if (natureOfBusinessType != null)
									natureOfProfession = natureOfBusinessType.getLabel();

								List<Incomes> incomesList = b.getIncomes();
								if (!incomesList.isEmpty()) {
									for (Incomes i : incomesList) {
										totalProfessionGrossReceipts = totalProfessionGrossReceipts
												+ i.getReceipts().intValue();
										totalProfessionPresumptiveIncome = totalProfessionPresumptiveIncome
												+ i.getPresumptiveIncome().intValue();
									}
								}
							}
						}
					}

					int totalLiabilities = 0;
					int totalAssets = 0;

					FinancialParticulars financialParticulars = business.getFinancialParticulars();

					if (financialParticulars != null) {

						totalLiabilities = financialParticulars.getMembersOwnCapital().intValue()
								+ financialParticulars.getSecuredLoans().intValue()
								+ financialParticulars.getUnSecuredLoans().intValue()
								+ financialParticulars.getAdvances().intValue()
								+ financialParticulars.getSundryCreditorsAmount().intValue()
								+ financialParticulars.getOtherLiabilities().intValue();

						totalAssets = financialParticulars.getFixedAssets().intValue()
								+ financialParticulars.getInventories().intValue()
								+ financialParticulars.getSundryDebtorsAmount().intValue()
								+ financialParticulars.getBalanceWithBank().intValue()
								+ financialParticulars.getCashInHand().intValue()
								+ financialParticulars.getLoanAndAdvances().intValue()
								+ financialParticulars.getOtherAssets().intValue();

					}

					summaryPdfXml.append("<totalLiabilities>" + totalLiabilities + "</totalLiabilities>");
					summaryPdfXml.append("<totalAssets>" + totalAssets + "</totalAssets>");

					summaryPdfXml.append("<businessName>" + businessName + "</businessName>");
					summaryPdfXml.append("<professionName>" + professionName + "</professionName>");

					summaryPdfXml.append("<natureOfBusiness>" + natureOfBusiness + "</natureOfBusiness>");
					summaryPdfXml.append("<natureOfProfession>" + natureOfProfession + "</natureOfProfession>");

					summaryPdfXml.append("<totalBusinessIncome>"
							+ (totalProfessionPresumptiveIncome + totalBusinessPresumptiveIncome)
							+ "</totalBusinessIncome>");

					summaryPdfXml.append("<totalProfessionGrossReceipts>" + totalProfessionGrossReceipts
							+ "</totalProfessionGrossReceipts>");
					summaryPdfXml.append("<totalProfessionPresumptiveIncome>" + totalProfessionPresumptiveIncome
							+ "</totalProfessionPresumptiveIncome>");

					summaryPdfXml.append("<totalBusinessGrossReceipts>" + totalBusinessGrossReceipts
							+ "</totalBusinessGrossReceipts>");
					summaryPdfXml.append("<totalBusinessPresumptiveIncome>" + totalBusinessPresumptiveIncome
							+ "</totalBusinessPresumptiveIncome>");
				}
			}

			boolean hasLossesToCarriedForward = false;
			BigDecimal zero = BigDecimal.valueOf(0);
			if ((summary.getHousePropertyLossesToBeCarriedForward() != null
					&& !summary.getHousePropertyLossesToBeCarriedForward().equals(zero))
					|| (summary.getShortTermCapitalGainLossesToBeCarriedForward() != null
							&& !summary.getShortTermCapitalGainLossesToBeCarriedForward().equals(zero))
					|| (summary.getLongTermCapitalGainLossesToBeCarriedForward() != null
							&& !summary.getLongTermCapitalGainLossesToBeCarriedForward().equals(zero))
					|| (summary.getBusinessProfessionalLossesToBeCarriedForward() != null
							&& !summary.getBusinessProfessionalLossesToBeCarriedForward().equals(zero))
					|| (summary.getSpeculativeBusinessLossesToBeCarriedForward() != null
							&& !summary.getSpeculativeBusinessLossesToBeCarriedForward().equals(zero))
					|| (summary.getHousePropertyLossesSetOffDuringTheYear() != null
							&& !summary.getHousePropertyLossesSetOffDuringTheYear().equals(zero))
					|| (summary.getShortTermCapitalGainLossesSetOffDuringTheYear() != null
							&& !summary.getShortTermCapitalGainLossesSetOffDuringTheYear().equals(zero))
					|| (summary.getLongTermCapitalGainLossesSetOffDuringTheYear() != null
							&& !summary.getLongTermCapitalGainLossesSetOffDuringTheYear().equals(zero))
					|| (summary.getBusinessProfessionalLossesSetOffDuringTheYear() != null
							&& !summary.getBusinessProfessionalLossesSetOffDuringTheYear().equals(zero))
					|| (summary.getSpeculativeBusinessLossesSetOffDuringTheYear() != null
							&& !summary.getSpeculativeBusinessLossesSetOffDuringTheYear().equals(zero))) {
				hasLossesToCarriedForward = true;
			}

			summaryPdfXml
					.append("<hasLossesToCarriedForward>" + hasLossesToCarriedForward + "</hasLossesToCarriedForward>");
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
		return summaryPdfXml.toString();
	}

	@Override
	public Summary getSummary(long summaryId) {

		Summary summaryData = summaryRepository.findBySummaryId(summaryId);
		if (summaryData != null)
			return summaryData;

		return null;
	}

	@Override
	public List<Summary> getAllSummaries() {

		return summaryRepository.findAll();

	}

//	@Override
//	public void deleteSummary(long summaryId) {
//
//		summaryRepository.deleteBySummaryId(summaryId);
//
//	}

	@Override
	public Object getDisapprovedSummaries() {

		List<Summary> disapprovedSummaryList = summaryRepository.findDisapprovedSummaries();

		List<Long> userIdList = new ArrayList<>();

		disapprovedSummaryList.forEach(s -> {

			userIdList.add(s.getUserId());

		});

		return userIdList;
	}

	@Override
	public Summary getSummaryByContactNumber(String contactNumber) {
		List<Summary> summaryList = summaryRepository.findByContactNumber(contactNumber);
		return summaryList.get(summaryList.size() - 1);
	}

	@Override
	public Summary findByItrId(int itrId) {
		return summaryRepository.findByItrId(itrId);
	}

//	@Override
//	public void test() {
//		List<Summary> summaryList = summaryRepository.findAll();
//		List<String[]> data = new ArrayList<>();
//
//		data.add(new String[] { "User Id", "Name", "Phone Number", "Email ID", "PAN", "DOB", "Gross Total Income",
//				"80C", "80CCD(1)", "80CCD(2)", "80CCD(1B)", "80D" });
//
//		SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
//
//		dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
//
//		for (Summary summary : summaryList) {
//			try {
//				if (summary.getUs80ccd1b() < 50000) {
//					String firstName = "";
//					String lastName = "";
//					String contactNumber = "";
//					String email = "";
//					String pan = "";
//					String dateOfBirth = "";
//					String gti = "";
//					String userId = "";
//
//					if (summary.getUserId() == 0) {
//						UserProfile userProfile = userProfileRepository
//								.findUserByMobile(summary.getAssesse().getContactNumber());
//						if (userProfile != null)
//							userId = String.valueOf(userProfile.getUserId());
//					} else
//						userId = String.valueOf(summary.getUserId());
//
//					if (summary.getTaxSummary() != null && summary.getTaxSummary().getGrossTotalIncome() != null)
//						gti = summary.getTaxSummary().getGrossTotalIncome().toString();
//
//					if (summary.getAssesse() != null) {
//						if (summary.getAssesse().getContactNumber() != null)
//							contactNumber = summary.getAssesse().getContactNumber();
//
//						if (summary.getAssesse().getEmail() != null)
//							email = summary.getAssesse().getEmail();
//
//						if (summary.getAssesse().getPanNumber() != null)
//							pan = summary.getAssesse().getPanNumber();
//
//						if (summary.getAssesse().getFamily() != null && !summary.getAssesse().getFamily().isEmpty()) {
//							Person person = summary.getAssesse().getFamily().get(0);
//							dateOfBirth = dateFormat.format(Date.from(Instant.parse(person.getDateOfBirth())));
//							firstName = person.getfName();
//							lastName = person.getlName();
//						}
//					}
//
//					data.add(new String[] { userId, firstName + " " + lastName, contactNumber, email, pan, dateOfBirth,
//							gti, String.valueOf(summary.getUs80c()), String.valueOf(summary.getUs80ccc1()),
//							String.valueOf(summary.getUs80ccd2()), String.valueOf(summary.getUs80ccd1b()),
//							String.valueOf(summary.getUs80d()) });
//				}
//			} catch (Exception e) {
//
//			}
//		}
//
//		try {
//			FileWriter fileWriter = new FileWriter(new File("C:\\Users\\Ajay\\Desktop\\Non NPS Summary.csv"));
//
//			CSVWriter writer = new CSVWriter(fileWriter);
//
//			writer.writeAll(data);
//
//			writer.close();
//
//			fileWriter.close();
//		} catch (Exception e) {
//			System.out.println("sum");
//
//		}
//	}

	private void addSummaryDataToGoogleSheet(Summary summary) {
		try {
			String firstName = "";
			String lastName = "";
			String contactNumber = "";
			String email = "";
			String pan = "";
			String dateOfBirth = "";
			String gti = "";
			String userId = "";

			if (summary.getUserId() == 0) {
				UserProfile userProfile = userProfileRepository
						.findUserByMobile(summary.getAssesse().getContactNumber());
				if (userProfile != null)
					userId = String.valueOf(userProfile.getUserId());
			} else
				userId = String.valueOf(summary.getUserId());

			if (summary.getTaxSummary() != null && summary.getTaxSummary().getGrossTotalIncome() != null)
				gti = summary.getTaxSummary().getGrossTotalIncome().toString();

			if (summary.getAssesse() != null) {
				if (summary.getAssesse().getContactNumber() != null)
					contactNumber = summary.getAssesse().getContactNumber();

				if (summary.getAssesse().getEmail() != null)
					email = summary.getAssesse().getEmail();

				if (summary.getAssesse().getPanNumber() != null)
					pan = summary.getAssesse().getPanNumber();

				if (summary.getAssesse().getFamily() != null && !summary.getAssesse().getFamily().isEmpty()) {
					SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
					dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
					Person person = summary.getAssesse().getFamily().get(0);
					dateOfBirth = dateFormat.format(Date.from(Instant.parse(person.getDateOfBirth())));
					firstName = person.getfName();
					lastName = person.getlName();
				}
			}

			String url = applicationProperties.getMycom().getUrl() + "user/update-nps-summary?values="
					+ URLEncoder.encode(userId, "UTF-8") + "," + URLEncoder.encode(firstName + " " + lastName, "UTF-8")
					+ "," + URLEncoder.encode(contactNumber, "UTF-8") + "," + URLEncoder.encode(email, "UTF-8") + ","
					+ URLEncoder.encode(pan, "UTF-8") + "," + URLEncoder.encode(dateOfBirth, "UTF-8") + ","
					+ URLEncoder.encode(gti, "UTF-8") + ","
					+ URLEncoder.encode(String.valueOf(summary.getUs80c()), "UTF-8") + ","
					+ URLEncoder.encode(String.valueOf(summary.getUs80ccc1()), "UTF-8") + ","
					+ URLEncoder.encode(String.valueOf(summary.getUs80ccd2()), "UTF-8") + ","
					+ URLEncoder.encode(String.valueOf(summary.getUs80ccd1b()), "UTF-8") + ","
					+ URLEncoder.encode(String.valueOf(summary.getUs80d()), "UTF-8");
			Unirest.get(url).asString();
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}

	}

	@Override
	public Summary findSummaryByItrId(long itrId) {

		return summaryRepository.findByItrId(itrId);
	}

	@Override
	public void sendDraftSummary(int userId, String assessmentYear, String isRevised, String channel,
			boolean sendPaymentAckMessage) throws Exception {

		Itr itr = itrRepository.findByUserIdAssessmentYearAndIsRevised(userId, assessmentYear, isRevised);

		if (itr == null)
			throw new DataNotFoundExpection("data not found: " + userId);

		calculatorService.calculateTax(itr, true);

		byte[] pdfByteArray = efillingResource.createJsp(itr.getUserId(), itr.getItrId(), assessmentYear, null, true)
				.getBody();

		if (Constants.MESSAGE_CHANNEL_CHATBUDDY.equalsIgnoreCase(channel) || "Both".equalsIgnoreCase(channel))
			sendDraftSummaryOnChat(pdfByteArray, userId, "ITR", sendPaymentAckMessage);
		else if (Constants.CHANNEL_WHATSAPP.equalsIgnoreCase(channel) || "Both".equalsIgnoreCase(channel))
			sendDraftSummaryOnWhatsapp(pdfByteArray, userId, "ITR", sendPaymentAckMessage);
		try {
			ItrFlowSummaryDTO itrFlowSummaryDTO = new ItrFlowSummaryDTO();
			itrFlowSummaryDTO.setUserId(itr.getUserId());
			itrFlowSummaryDTO.setAssessmentYear(itr.getAssessmentYear());
			itrFlowSummaryDTO.setChangeSuccessorTaskStatus(true);
			itrFlowSummaryDTO.setTaskKeyName(Constants.TASK_KEY_NAME_DRAFT_SUMMARY_SENT_FOR_APPROVAL);
			itrFlowSummaryDTO.setTaskStatus(Constants.TASK_STATUS_COMPLETED);
			Utils.updateItrLifecycleStatus(itrFlowSummaryDTO, applicationProperties.getMycom().getEvn());
		} catch (Exception e) {
			log.error("Error while updating itrlifecyclestatus fetchPrefill: " + e.getMessage());
		}
	}

	private void sendDraftSummaryOnWhatsapp(byte[] pdfByteArray, int userId, String string,
											boolean sendPaymentAckMessage) {
		AgentDetailsDTO assignedDetails = null;
		if ((assignedDetails = agentAssignmentDetailsService.getAgentDetailsByUserIdAndServiceType(userId,
				"ITR")) != null) {
			String requestId = assignedDetails.getRequestId();
			if (sendPaymentAckMessage) {
				chatService.sendTextMessage(userId, "ITR",
						"From the prefilled data, we have received the following information-\n");
			}
			if (pdfByteArray != null) {
				chatService.uploadFileToChatBuddy(pdfByteArray, requestId, "DraftSummary.pdf");
			}
			chatService.sendRichMessage(userId, "ITR", "", Constants.MESSAGE_PURPOSE_DRAFT_SUMMARY);
		}

	}
	
	@Override
	public byte[] generateDraftSummaryPdf(int userId, String assessmentYear, String isRevised) throws FOPException, IOException, TransformerException {
		Itr itr = itrRepository.findByUserIdAssessmentYearAndIsRevised(userId, assessmentYear, isRevised);
		
		if(itr == null)
			throw new DataNotFoundExpection("data not found: "+userId);
		
		if(itr.getSystemFlags() == null)
			itr.setSystemFlags(new SystemFlag());
		
		Assessment assessment = calculatorService.calculateTax(itr, false);
		
		return generateDraftSummaryPdf(itr, assessment);
	}
	
	private byte[] generateDraftSummaryPdf(Itr itr, Assessment assessment) throws IOException, FOPException, TransformerException {
		
			SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");
			
			JSONObject itrJson = new JSONObject();

			Optional<Family> family = itr.getFamily().stream().filter(fam -> fam.getRelationType().equals("SELF"))
					.findAny();
			
			if(!family.isPresent())
				throw new BadRequestAlertException("family object is not present in itr object", "itr");
				
			String firstName = "";
			String lastName = "";
			String dateOfBirth = "";
			String fatherName = "";
			
			if(family.get().getfName() != null)
				firstName = family.get().getfName();
			
			if(family.get().getlName() != null)
				lastName = family.get().getlName();
			
			if(family.get().getDateOfBirth() != null)
				dateOfBirth = formatter.format(Date.from(family.get().getDateOfBirth()));
			
			if(family.get().getFatherName() != null)
				fatherName = family.get().getFatherName();
			
			double cgIncomeAtNormalRate = assessment.getSummaryIncome().getCgIncomeN().getCapitalGain().stream().filter(cg->cg.getTaxRate()==-1 && cg.getCgIncome() != null).mapToDouble(cg->cg.getCgIncome().doubleValue()).sum();
			double cgIncomeAt15Per = assessment.getSummaryIncome().getCgIncomeN().getCapitalGain().stream().filter(cg->cg.getTaxRate()==15 && cg.getCgIncome() != null).mapToDouble(cg->cg.getCgIncome().doubleValue()).sum();
			double cgIncomeAt10Per = assessment.getSummaryIncome().getCgIncomeN().getCapitalGain().stream().filter(cg->cg.getTaxRate()==10 && cg.getCgIncome() != null).mapToDouble(cg->cg.getCgIncome().doubleValue()).sum();
			double cgIncomeAt20Per = assessment.getSummaryIncome().getCgIncomeN().getCapitalGain().stream().filter(cg->cg.getTaxRate()==20 && cg.getCgIncome() != null).mapToDouble(cg->cg.getCgIncome().doubleValue()).sum();

			double bp44ad = assessment.getSummaryIncome().getSummaryBusinessIncome().getIncomes().stream().filter(income->income.getBusinessType().equals(ItrConstants.BUSINESS_TYPE_BUSINESS)).mapToDouble(income->income.getReceipts().doubleValue()).sum();
			double bp44ada = assessment.getSummaryIncome().getSummaryBusinessIncome().getIncomes().stream().filter(income->income.getBusinessType().equals(ItrConstants.BUSINESS_INCOME_TYPE_PROFESSIONAL)).mapToDouble(income->income.getReceipts().doubleValue()).sum();
			double totalNonSpeculative = assessment.getSummaryIncome().getSummaryBusinessIncome().getTotalNonSpeculativeIncome().doubleValue();
			double totalSpeculative = assessment.getSummaryIncome().getSummaryBusinessIncome().getTotalSpeculativeIncome().doubleValue();
			double totalBusinessIncome = assessment.getSummaryIncome().getSummaryBusinessIncome().getTotalBusinessIncome().doubleValue();
			double totalOtherTaxableIncome = assessment.getSummaryIncome().getSummaryOtherIncome().getTotalOtherTaxableIncome().doubleValue();

			itrJson.put("cgIncomeAtNormalRate", cgIncomeAtNormalRate);
			itrJson.put("cgIncomeAt15Per", cgIncomeAt15Per);
			itrJson.put("cgIncomeAt10Per", cgIncomeAt10Per);
			itrJson.put("cgIncomeAt20Per", cgIncomeAt20Per);
			itrJson.put("bp44ad", bp44ad);
			itrJson.put("bp44ada", bp44ada);
			itrJson.put("totalNonSpeculative", totalNonSpeculative);
			itrJson.put("totalSpeculative", totalSpeculative);
			itrJson.put("totalBusinessIncome", totalBusinessIncome);
			itrJson.put("totalOtherTaxableIncome", totalOtherTaxableIncome);
			itrJson.put("name", firstName+" "+lastName);
			itrJson.put("panNumber", itr.getPanNumber());
			itrJson.put("dateOfBirth", dateOfBirth);
			itrJson.put("residentialStatus", itr.getResidentialStatus());
			itrJson.put("assessmentYear", itr.getAssessmentYear());
			itrJson.put("financialYear", Utils.getFinancialCurrentYear());
			itrJson.put("email", itr.getEmail());
			itrJson.put("aadharNumber", itr.getAadharNumber());
			itrJson.put("contactNumber", itr.getContactNumber());
			itrJson.put("fatherName", fatherName);
			itrJson.put("totalCapitalGain", assessment.getTaxSummary().getCapitalGain());
			itrJson.put("otherIncome", assessment.getTaxSummary().getOtherIncome());
			itrJson.put("totalIncome", assessment.getTaxSummary().getTotalIncome());
			itrJson.put("totalHpIncome", assessment.getSummaryIncome().getSummaryHpIncome().getTotalIncome());
			itrJson.put("totalSummarySalaryTaxableIncome", assessment.getSummaryIncome().getSummarySalaryIncome().getTotalSummarySalaryTaxableIncome());
			itrJson.put("totalTaxesPaid", assessment.getTaxSummary().getTotalTaxesPaid());
			
			if(itr.getAddress() != null)
				itrJson.put("address", new JSONObject(itr.getAddress()));

			if(CollectionUtils.isEmpty(itr.getEmployers()))
				itrJson.put("employerList", new JSONArray(new ObjectMapper().writeValueAsString(assessment.getSummaryIncome().getSummarySalaryIncome().getEmployers())));
			
			if(itr.getTaxPaid() != null)
				itrJson.put("taxPaid", new JSONObject(itr.getTaxPaid()));
			
			if(CollectionUtils.isEmpty(itr.getPastYearLosses()))
				itrJson.put("pastYearLosses", new JSONArray(itr.getPastYearLosses()));
			
			String itrXml = XML.toString(itrJson);
			
			String xml = "<?xml version = \"1.0\"?>\r\n"
					+ "<?xml-stylesheet type = \"text/xsl\" href = \"DraftSummary.xsl\"?>\r\n" + "<summary>\r\n" + itrXml
					+ "</summary>";

			String xsl = IOUtils.toString(getClass().getResourceAsStream("/XSLFiles/DraftSummary.xsl"),
					Charset.defaultCharset());

			return Utils.generatePdf(xml, xsl);
	}

	private void sendDraftSummaryOnChat(byte[] file, int userId, String serviceType, boolean sendPaymentAckMessage)
			throws Exception {
		AgentDetailsDTO assignedDetails = null;
		if ((assignedDetails = agentAssignmentDetailsService.getAgentDetailsByUserIdAndServiceType(userId,
				serviceType)) != null) {
			String requestId = assignedDetails.getRequestId();
			if (sendPaymentAckMessage) {
				chatService.sendTextMessage(userId, serviceType,
						"From the prefilled data, we have received the following information-\n");
			}
			if (file != null) {
				chatService.uploadFileToChatBuddy(file, requestId, "DraftSummary.pdf");
			}
			chatService.sendRichMessage(userId, serviceType, "", Constants.MESSAGE_PURPOSE_DRAFT_SUMMARY);
		}
	}

	@Override
	public byte[] downloadItrSummaryJsonPdf(int itrId) throws IOException, FOPException, TransformerException {
		Itr itr = itrRepository.findItrByItrId(itrId);

		if (itr == null)
			throw new DataNotFoundExpection("Itr not found for: " + itrId);

		if (MapUtils.isEmpty(itr.getItrSummaryJson()))
			throw new DataNotFoundExpection(
					"The itrId provided does not have the ITR Summary JSON within the itr object: " + itrId);

		String summaryPdfXml = generatItrSummaryJsonPdfXML(itr.getItrSummaryJson(), itr);

		Pattern pattern = Pattern.compile("[^\\u0009\\u000A\\u000D\\u0020-\\uD7FF\\uE000-\\uFFFD\\u10000-\\u10FFF]+");
		summaryPdfXml = pattern.matcher(summaryPdfXml).replaceAll("");
		summaryPdfXml = summaryPdfXml.replace("&", "&amp;");

		String xml = "<?xml version = \"1.0\"?>\r\n" + "<?xml-stylesheet type = \"text/xsl\" href = \"ITR"
				+ itr.getItrType() + "JsonSummary.xsl\"?>\r\n" + "<summary>\r\n" + summaryPdfXml + "</summary>";

		String xsl = IOUtils.toString(
				getClass().getResourceAsStream("/XSLFiles/ITR" + itr.getItrType() + "JsonSummary.xsl"),
				Charset.defaultCharset());

		if (!xsl.isEmpty() && !xml.isEmpty())
			return Utils.generatePdf(xml, xsl);

		return new byte[0];
	}

	private String generatItrSummaryJsonPdfXML(Map<String, Object> itrSummaryJson, Itr itr)
			throws JsonProcessingException {
		Document summaryJson = Document.parse(new ObjectMapper().writeValueAsString(itrSummaryJson));
		Document itrJson = summaryJson.get("ITR", Document.class).get("ITR" + itr.getItrType(), Document.class);
		itrJson.put("assessmentYear", itr.getAssessmentYear());
		itrJson.put("financialYear", itr.getFinancialYear());
		itrJson.put("regime", itr.getRegime());

		itrJson.put("houseRentAllowance", 0);
		itrJson.put("otherAllowance", 0);
		itrJson.put("itrType", itr.getItrType());

		int sumOfUs80cUs80cccUs80ccc1 = 0;
		int sumOfUs80ggaUs80ggc = 0;
		int us80ttaTtb = 0;
		int preventiveHealthCheckupForWholeFamily = 0;
		int medicalExpenditure = 0;
		int totalIntrestPay = 0;
		int grossTrunover44AD = 0;
		int lossesSetOffDuringTheYear = 0;
		int shortTermCapitalGainTotal = 0;
		int shortTermCapitalGainAt15PercentTotal = 0;
		int longTermCapitalGainAt10PercentTotal = 0;
		int longTermCapitalGainAt20PercentTotal = 0;
		int houseRentAllowance = 0;
		int otherAllowance = 0;
		int tradingExpenses = 0;
		int otherLiabilities = 0;
		int anyOtherIncome = 0;
		int totalLiabilities = 0;
		int totalAssets = 0;
		int totalVda = 0;
		int familyPensionIncome = 0;
		int immovableAssetTotal = 0;
		int movableAssetTotal = 0;
		int totalVDACapitalGain = 0;
		int totalVDABusinessIncome = 0;
		int otherIncomeExcludefamilypension = 0;
		int grsTrnOverBank = 0;
		int grsTotalTrnOverInCashAndOtherInc = 0;
		int persumptiveInc44AD6Per = 0;
		int persumptiveInc44AD8Per = 0;
		int totPersumptiveInc44AD = 0;
		int grsTrnOverOrReceipt = 0;
		List<Document> partPLNatOf44AD = null;
		List<Document> partPLNatOf44ADA = null;
		int grsReceiptITR3 = 0;
		int grsReceiptITR4 = 0;
		int totPersumptiveInc44ADA = 0;
		int totPersumptiveInc44ADAITR4 = 0;
		List<Document> itrFourPartPLNatOf44AD = null;
		List<Document> itrFourPartPLNatOf44ADA = null;
		int grsTrnOverBankItrFour = 0;
		int persumptiveInc44AD6PerItrFour = 0;
		int grsTotalCashAndOtherIncITRFour = 0;
		int persumptiveInc44AD8PerItrFour = 0;
		int totPersumptiveInc44ADItrFour = 0;
		int grsTrnOverOrReceiptItrFour = 0;
		int totalProfBusGainIncomeITR3 = 0;
		int age = 0;
		int taxPayableOnNormalIncome = 0;
		int totalNormalGrossIncome = 0;
		int totalNormalTaxableIncome = 0;

		boolean residentSeniorCitizen = false;
		List<Document> donations = new ArrayList<>();
		
		 String itrType = itr.getItrType();
	        Map<String, Object> scheduleDPMObj = null;
	        Map<String, Object> scheduleDOAObj = null;
	        try {
	            Map<String, Object> itrMap = (Map<String, Object>) itrSummaryJson.get("ITR");
	            if (itrMap != null) {
	                Map<String, Object> itrTypeMap = (Map<String, Object>) itrMap.get("ITR"+itrType);
	                if (itrTypeMap != null) {
	                    scheduleDPMObj = (Map<String, Object>) itrTypeMap.get("ScheduleDPM");
	                    scheduleDOAObj = (Map<String, Object>) itrTypeMap.get("ScheduleDOA");
	                }
	            }
	        } catch (ClassCastException e) {
	            e.printStackTrace();
	        }

	        Map<String, Object> scheduleData = new HashMap<>();

	     if (scheduleDPMObj != null && scheduleDOAObj != null) {
	         scheduleData.put("ScheduleDPM", scheduleDPMObj);
	         scheduleData.put("ScheduleDOA", scheduleDOAObj);
	     } 

	        List<DepreciationDetailsDTO> depreciationList = getDepreciationDetails(scheduleData);
	    
		List<Document> depreciationDocumentList = new ArrayList<>();
		for (DepreciationDetailsDTO dto : depreciationList) {
		    Document doc = new Document();
		    doc.put("wdvFirstDay", dto.getWdvFirstDay().toString());
		    doc.put("additionDuringTheYear", dto.getAdditionDuringTheYear().toString());
		    doc.put("realizationTotalPeriod", dto.getRealizationTotalPeriod().toString());
		    doc.put("depreciationAmount", dto.getDepreciationAmount().toString());
		    doc.put("depreciation", dto.getDepreciation().toString());
		    doc.put("wdvLastDay", dto.getWdvLastDay().toString());
		    doc.put("assetGroup", dto.getAssetGroup());

		    depreciationDocumentList.add(doc);
		}
		List<String> assetOrder = List.of(
			    "Laptop and Computers",
			    "Plant and Machinery (at 15%)",
			    "Plant and Machinery (at 30%)",
			    "Plant and Machinery (at 40%)",
			    "Furniture and Fittings",
			    "Land and Building (at 0%)",
			    "Land and Building (at 5%)",
			    "Land and Building (at 10%)",
			    "Land and Building (at 40%)",
			    "Intangible Assets"
			);

		depreciationDocumentList = depreciationDocumentList.stream()
			    .filter(doc -> assetOrder.contains(doc.getString("assetGroup")))
			    .sorted(Comparator.comparing(doc -> assetOrder.indexOf(doc.getString("assetGroup"))))
			    .collect(Collectors.toList());

			itrJson.put("depreciationList", depreciationDocumentList);
		
		int dividendIncomes = 0;
		if (itr.getDividendIncomes() != null && !itr.getDividendIncomes().isEmpty()) {
			 for (DividendIncome dividendIncome : itr.getDividendIncomes()) {
			        BigDecimal income = dividendIncome.getIncome();
			        dividendIncomes += income != null ? income.intValue() : 0;  
			    }
		    itrJson.put("dividendIncomes", dividendIncomes);
		}
		boolean isITRU = false;
		Document personalInfomation = itrJson.get("PersonalInfo", Document.class);
		if (personalInfomation != null) {
			String dob = personalInfomation.getString("DOB");
			if (dob != null) {
				dob = dob.trim();
				age = Period.between(LocalDate.parse(dob, DateTimeFormatter.ofPattern("yyyy-MM-dd")), LocalDate.now())
						.getYears();
			}
		}
		if (age >= 60 && itr.getResidentialStatus() != null
		        && itr.getResidentialStatus().equals("RESIDENT") &&
		        (itr.getItrType().equals("1") || itr.getItrType().equals("2"))) {
			residentSeniorCitizen = true;

		}
		itrJson.put("residentSeniorCitizen", residentSeniorCitizen);
		if (itrJson != null) {
			Document schedule80G = itrJson.get("Schedule80G", Document.class);

		    if (schedule80G != null) {
		        int totalDonationsUs80G = schedule80G.getInteger("TotalDonationsUs80G");
		        int totalEligibleDonationsUs80G = schedule80G.getInteger("TotalEligibleDonationsUs80G");

		        itrJson.put("totalDonationsUs80G", totalDonationsUs80G);
		        itrJson.put("totalEligibleDonationsUs80G", totalEligibleDonationsUs80G);
		    }

		    Document incomeDeductions = itrJson.get("ITR"+itr.getItrType()+"_IncomeDeductions", Document.class);
		    if (incomeDeductions != null) {
		    	int grossTotIncome = incomeDeductions.getInteger("GrossTotIncome");
		        itrJson.put("grossTotIncome", grossTotIncome);
		    }
		}
		boolean isSchemeValid = false;
		Collection<DonationType> donationsList = itr.getDonations();
		for (DonationType donation : donationsList) {
			String schemeCode = donation.getSchemeCode();
            if (schemeCode != null && !schemeCode.isEmpty()) {
            	if ("GOVT_APPRVD_FAMLY_PLNG".equals(schemeCode) || "FND_SEC80G".equals(schemeCode)) {
            		isSchemeValid = true;
                }
            }
        }
		 itrJson.put("isSchemeValid", isSchemeValid);
		if (itr.getItrType().equals("1") || itr.getItrType().equals("4")) {

			Document filingStatus = itrJson.get("FilingStatus", Document.class);

			if (21 == filingStatus.getInteger("ReturnFileSec", 0))
				isITRU = true;

			Document personalInfo = itrJson.get("PersonalInfo", Document.class);
			Document address = personalInfo.get("Address", Document.class);

			if (personalInfo.containsKey("EmployerCategory"))
				itrJson.put("employerCategory", personalInfo.getString("EmployerCategory"));
			else
				itrJson.put("employerCategory", "NA");

			itrJson.put("state", invoiceServiceV1.getStateByStateCode(address.getString("StateCode")));

			if (!"91".equals(address.getString("CountryCode")))
				itrJson.put("country", "");
			else
				itrJson.put("country", "India");

			Document taxesPaid = itrJson.get("TaxPaid", Document.class).get("TaxesPaid", Document.class);

			itrJson.put("totalAdvanceTax",
					taxesPaid.getInteger("AdvanceTax", 0) + taxesPaid.getInteger("SelfAssessmentTax", 0));

			Document itrincomeDeductions;
			if (itr.getItrType().equals("1"))
				itrincomeDeductions = itrJson.get("ITR1_IncomeDeductions", Document.class);
			else
				itrincomeDeductions = itrJson.get("IncomeDeductions", Document.class);

			if (itrincomeDeductions.containsKey("AllwncExemptUs10")) {
				Document allwncExemptUs10 = itrincomeDeductions.get("AllwncExemptUs10", Document.class);
				List<Document> allwncExemptUs10Dtls = allwncExemptUs10.getList("AllwncExemptUs10Dtls", Document.class,
						new ArrayList<>());
				itrJson.put("houseRentAllowance",
						allwncExemptUs10Dtls.stream()
								.filter(allowance -> allowance.getString("SalNatureDesc").equals("10(13A)"))
								.mapToInt(allowance -> allowance.getInteger("SalOthAmount")).findFirst().orElse(0));
				itrJson.put("otherAllowance",
						allwncExemptUs10Dtls.stream()
								.filter(allowance -> !allowance.getString("SalNatureDesc").equals("10(13A)"))
								.mapToInt(allowance -> allowance.getInteger("SalOthAmount")).sum());
			}

			if (itrincomeDeductions.containsKey("UsrDeductUndChapVIA")) {
				Document userDeductUndChapVIA = itrincomeDeductions.get("UsrDeductUndChapVIA", Document.class);
				if (userDeductUndChapVIA.containsKey("Section80G")
						&& userDeductUndChapVIA.getInteger("Section80G", 0) != 0)
					schedule80G(itrJson, donations);
			}

			if (itrincomeDeductions.containsKey("DeductUndChapVIA")) {
				Document deductUndChapVIA = itrincomeDeductions.get("DeductUndChapVIA", Document.class);
				sumOfUs80cUs80cccUs80ccc1 = deductUndChapVIA.getInteger("Section80C", 0)
						+ deductUndChapVIA.getInteger("Section80CCC", 0)
						+ deductUndChapVIA.getInteger("Section80CCDEmployeeOrSE", 0);

				sumOfUs80ggaUs80ggc = deductUndChapVIA.getInteger("Section80GGA", 0)
						+ deductUndChapVIA.getInteger("Section80GGC", 0);

				us80ttaTtb = deductUndChapVIA.getInteger("Section80TTA", 0)
						+ deductUndChapVIA.getInteger("Section80TTB", 0);
			}

			if ("4".equals(itr.getItrType())) {
				Document intrstPay = itrJson.get("TaxComputation", Document.class).get("IntrstPay", Document.class);
				totalIntrestPay = intrstPay.getInteger("IntrstPayUs234A", 0)
						+ intrstPay.getInteger("IntrstPayUs234B", 0) + intrstPay.getInteger("IntrstPayUs234C", 0)
						+ intrstPay.getInteger("LateFilingFee234F", 0);
				if (itrJson.containsKey("ScheduleBP")) {
					Document scheduleBP = itrJson.get("ScheduleBP", Document.class);
					if (scheduleBP.containsKey("PersumptiveInc44AD")) {
						Document persumptiveInc44AD = scheduleBP.get("PersumptiveInc44AD", Document.class);
						grossTrunover44AD = persumptiveInc44AD.getInteger("GrsTrnOverBank", 0)
								+ persumptiveInc44AD.getInteger("GrsTrnOverAnyOthMode", 0);
					}

					Document itrMaster = mongoTemplate.findAll(Document.class, "itrMaster").get(0);

					List<Document> natureOfBusinesses = itrMaster.getList("natureOfBusiness", Document.class);

					if (scheduleBP.containsKey("NatOfBus44AD")) {
						List<Document> natOfBus44ADList = scheduleBP.getList("NatOfBus44AD", Document.class);
						for (Document natOfBus44AD : natOfBus44ADList) {
							Optional<Document> natureOfBusiness = natureOfBusinesses.stream()
									.filter(nob -> nob.getString("type").equals("BUSINESS")
											&& nob.getString("code").equals(natOfBus44AD.getString("CodeAD")))
									.findAny();
							if (natureOfBusiness.isPresent())
								natOfBus44AD.put("natureOfBusiness", natureOfBusiness.get().getString("label"));
						}
					}

					if (scheduleBP.containsKey("NatOfBus44ADA")) {
						List<Document> natOfBus44ADList = scheduleBP.getList("NatOfBus44ADA", Document.class);
						for (Document natOfBus44AD : natOfBus44ADList) {
							Optional<Document> natureOfBusiness = natureOfBusinesses.stream()
									.filter(nob -> nob.getString("type").equals("PROFESSION")
											&& nob.getString("code").equals(natOfBus44AD.getString("CodeADA")))
									.findAny();
							if (natureOfBusiness.isPresent())
								natOfBus44AD.put("natureOfBusiness", natureOfBusiness.get().getString("label"));
						}
					}
					if (scheduleBP != null) {
						itrFourPartPLNatOf44AD = scheduleBP.getList("NatOfBus44AD", Document.class);
						itrFourPartPLNatOf44ADA = scheduleBP.getList("NatOfBus44ADA", Document.class);

						if (scheduleBP.containsKey("PersumptiveInc44AD")) {

							Document persumptiveInc44AD = scheduleBP.get("PersumptiveInc44AD", Document.class);
							if (persumptiveInc44AD != null) {
								grsTrnOverBankItrFour = persumptiveInc44AD.getInteger("GrsTrnOverBank", 0);
								int grsTrnOverAnyOthModeItrFour = persumptiveInc44AD.getInteger("GrsTrnOverAnyOthMode",
										0);
								int grsTotalTrnOverInCashItrFour = persumptiveInc44AD
										.getInteger("GrsTotalTrnOverInCash", 0);
								grsTotalCashAndOtherIncITRFour = grsTrnOverAnyOthModeItrFour
										+ grsTotalTrnOverInCashItrFour;
								persumptiveInc44AD6PerItrFour = persumptiveInc44AD.getInteger("PersumptiveInc44AD6Per",
										0);
								persumptiveInc44AD8PerItrFour = persumptiveInc44AD.getInteger("PersumptiveInc44AD8Per",
										0);
								grsTrnOverOrReceiptItrFour = persumptiveInc44AD.getInteger("GrsTotalTrnOver", 0);
								totPersumptiveInc44ADItrFour = persumptiveInc44AD.getInteger("TotPersumptiveInc44AD",
										0);
							}
						}
						if (scheduleBP.containsKey("PersumptiveInc44ADA")) {

							Document persumptiveInc44ADA = scheduleBP.get("PersumptiveInc44ADA", Document.class);
							if (persumptiveInc44ADA != null) {
								grsReceiptITR4 = persumptiveInc44ADA.getInteger("GrsReceipt", 0);
								totPersumptiveInc44ADAITR4 = persumptiveInc44ADA.getInteger("TotPersumptiveInc44ADA",
										0);
							}
						}
					}
				}
			}
		} else {

			Document partAGen2 = itrJson.get("PartA_GEN1", Document.class);
			Document personalInfo = partAGen2.get("PersonalInfo", Document.class);
			Document address = personalInfo.get("Address", Document.class);

			Document filingStatus = partAGen2.get("FilingStatus", Document.class);

			if (21 == filingStatus.getInteger("ReturnFileSec", 0))
				isITRU = true;

			String residentialStatus = "Resident";

			if (filingStatus.containsKey("ResidentialStatus")) {
				if (filingStatus.getString("ResidentialStatus").equals(ItrJsonConstants.RESIDENTIAL_STATUS_NRI))
					residentialStatus = "Non Resident";
				else if (filingStatus.getString("ResidentialStatus").equals(ItrJsonConstants.RESIDENTIAL_STATUS_NOR))
					residentialStatus = "Non Ordinary";
			}

			itrJson.put("residentialStatus", residentialStatus);

			itrJson.put("state", invoiceServiceV1.getStateByStateCode(address.getString("StateCode")));

			if (filingStatus.get("JurisdictionResPrevYr", Document.class) != null) {
				Document jurisdictionResPrevYr = filingStatus.get("JurisdictionResPrevYr", Document.class);
				List<Document> jurisdictionResPrevYrDtls = jurisdictionResPrevYr.getList("JurisdictionResPrevYrDtls",
						Document.class);

				String countryCode = jurisdictionResPrevYrDtls.get(0).getString("JurisdictionResidence");

				String countryName = Utils.getCountryNameByCountryCode(countryCode);

				itrJson.put("countryName", countryName);
			}

			if ("91".equals(address.getString("CountryCode")))
				itrJson.put("country", "India");

			Document partBTTI = itrJson.get("PartB_TTI", Document.class);
			
			taxPayableOnNormalIncome = partBTTI.get("ComputationOfTaxLiability", Document.class).get("TaxPayableOnTI", Document.class).getInteger("TaxAtNormalRatesOnAggrInc", 0);

			Document taxesPaid = partBTTI.get("TaxPaid", Document.class).get("TaxesPaid", Document.class);

			itrJson.put("totalAdvanceTax",
					taxesPaid.getInteger("AdvanceTax", 0) + taxesPaid.getInteger("SelfAssessmentTax", 0));

			if (itrJson.containsKey("ScheduleVIA")) {
				Document scheduleVIA = itrJson.get("ScheduleVIA", Document.class);

				if (scheduleVIA.containsKey("UsrDeductUndChapVIA")) {
					Document userDeductUndChapVIA = scheduleVIA.get("UsrDeductUndChapVIA", Document.class);
					if (userDeductUndChapVIA.containsKey("Section80G")
							&& userDeductUndChapVIA.getInteger("Section80G", 0) != 0)
						schedule80G(itrJson, donations);
				}

				if (scheduleVIA.containsKey("DeductUndChapVIA")) {
					Document deductUndChapVIA = scheduleVIA.get("DeductUndChapVIA", Document.class);
					sumOfUs80cUs80cccUs80ccc1 = deductUndChapVIA.getInteger("Section80C", 0)
							+ deductUndChapVIA.getInteger("Section80CCC", 0)
							+ deductUndChapVIA.getInteger("Section80CCDEmployeeOrSE", 0);

					sumOfUs80ggaUs80ggc = deductUndChapVIA.getInteger("Section80GGA", 0)
							+ deductUndChapVIA.getInteger("Section80GGC", 0);

					us80ttaTtb = deductUndChapVIA.getInteger("Section80TTA", 0)
							+ deductUndChapVIA.getInteger("Section80TTB", 0);
				}
			}

			if (itrJson.containsKey("ScheduleAL")) {
				Document scheduleAL = itrJson.get("ScheduleAL", Document.class);
				if (scheduleAL.containsKey("ImmovableDetails")) {
					List<Document> immovableDetails = scheduleAL.getList("ImmovableDetails", Document.class);
					immovableAssetTotal = immovableDetails.stream().mapToInt(im -> im.getInteger("Amount", 0)).sum();
				}

				if (scheduleAL.containsKey("MovableAsset")) {
					Document movableAsset = scheduleAL.get("MovableAsset", Document.class);
					movableAssetTotal = movableAsset.getInteger("DepositsInBank", 0)
							+ movableAsset.getInteger("SharesAndSecurities", 0)
							+ movableAsset.getInteger("InsurancePolicies", 0)
							+ movableAsset.getInteger("LoansAndAdvancesGiven", 0)
							+ movableAsset.getInteger("CashInHand", 0)
							+ movableAsset.getInteger("JewelleryBullionEtc", 0)
							+ movableAsset.getInteger("ArchCollDrawPaintSulpArt", 0)
							+ movableAsset.getInteger("VehiclYachtsBoatsAircrafts", 0);
				}
			}

			Document partBTi = itrJson.get("PartB-TI", Document.class);
			totalNormalGrossIncome = partBTi.getInteger("GrossTotalIncome", 0) - partBTi.getInteger("IncChargeTaxSplRate111A112", 0);
			totalNormalTaxableIncome = partBTi.getInteger("AggregateIncome", 0);

			lossesSetOffDuringTheYear = partBTi.getInteger("TotalTI", 0) - partBTi.getInteger("BalanceAfterSetoffLosses", 0);
			Document capGain = partBTi.get("CapGain", Document.class);

			Document shortTerm = capGain.get("ShortTerm", Document.class);
			shortTermCapitalGainTotal = shortTerm.getInteger("ShortTermAppRate", 0);
			shortTermCapitalGainAt15PercentTotal = shortTerm.getInteger("ShortTerm15Per", 0);

			Document longTerm = capGain.get("LongTerm", Document.class);
			longTermCapitalGainAt10PercentTotal = longTerm.getInteger("LongTerm10Per", 0);
			longTermCapitalGainAt20PercentTotal = longTerm.getInteger("LongTerm20Per", 0);

			totalVDACapitalGain = capGain.getInteger("CapGains30Per115BBH", 0);
			if ("3".equals(itr.getItrType()) && partBTi.containsKey("ProfBusGain")) {
				Document profBusGain = partBTi.get("ProfBusGain", Document.class);
				totalVDABusinessIncome = profBusGain.getInteger("ProfIncome115BBF", 0);
				int totProfBusGain = profBusGain.getInteger("TotProfBusGain", 0);
				totalProfBusGainIncomeITR3 = totProfBusGain + totalVDABusinessIncome;
			}

			if (itrJson.containsKey("ScheduleOS")) {
				Document scheduleOS = itrJson.get("ScheduleOS", Document.class);
				if (scheduleOS.containsKey("IncOthThanOwnRaceHorse")) { // IncOthThanOwnRaceHorse
					Document incOthThanOwnRaceHorse = scheduleOS.get("IncOthThanOwnRaceHorse", Document.class);
					if (incOthThanOwnRaceHorse != null) {
						anyOtherIncome = incOthThanOwnRaceHorse.getInteger("BalanceNoRaceHorse", 0)
								- incOthThanOwnRaceHorse.getInteger("DividendGross", 0)
								- incOthThanOwnRaceHorse.getInteger("IntrstFrmSavingBank", 0)
								- incOthThanOwnRaceHorse.getInteger("IntrstFrmTermDeposit", 0)
								- incOthThanOwnRaceHorse.getInteger("IntrstFrmIncmTaxRefund", 0)
								- incOthThanOwnRaceHorse.getInteger("FamilyPension", 0);
					}
				}
			}

			if (itrJson.containsKey("ScheduleCFL"))
				scheduleCFL(itrJson);

			if ("3".equals(itr.getItrType()))
				if (itrJson.containsKey("ScheduleCFL"))
					scheduleCFLForITR3(itrJson);

			if (itrJson.containsKey("ScheduleS")) {
				Document scheduleS = itrJson.get("ScheduleS", Document.class);
				if (scheduleS.containsKey("AllwncExemptUs10")) {
					Document allwncExemptUs10 = scheduleS.get("AllwncExemptUs10", Document.class);
					List<Document> allwncExemptUs10Dtls = allwncExemptUs10.getList("AllwncExemptUs10Dtls",
							Document.class);
					houseRentAllowance = allwncExemptUs10Dtls.stream()
							.filter(allw -> allw.getString("SalNatureDesc").equals("10(13A)"))
							.mapToInt(allw -> allw.getInteger("SalOthAmount", 0)).findAny().orElse(0);
				}

				otherAllowance = scheduleS.getInteger("AllwncExtentExemptUs10", 0) - houseRentAllowance;
			}
			if (itrJson.containsKey("ScheduleS")) {
				Document scheduleS = itrJson.get("ScheduleS", Document.class);
				if (scheduleS.containsKey("Salaries")) {
					List<Document> salaries = scheduleS.getList("Salaries", Document.class, new ArrayList<>());
					Document salarys = salaries.get(0).get("Salarys", Document.class);
					if (salarys.containsKey("NatureOfSalary")) {
						Document natureOfSalary = salarys.get("NatureOfSalary", Document.class);
						List<Document> othersIncDtls = natureOfSalary.getList("OthersIncDtls", Document.class,
								new ArrayList<>());
						for (Document othersIncDtl : othersIncDtls) {
							String natureDescNames = getSalDescName(othersIncDtl.getString("NatureDesc"));
							othersIncDtl.put("natureDescNames", natureDescNames);
//						System.out.println(natureDescNames);
						}
					}
					if (salarys.containsKey("NatureOfProfitInLieuOfSalary")) {
						Document natureOfProfitInLieuOfSalary = salarys.get("NatureOfProfitInLieuOfSalary",
								Document.class);
						List<Document> othersIncDtls = natureOfProfitInLieuOfSalary.getList("OthersIncDtls",
								Document.class, new ArrayList<>());
						for (Document othersIncDtl : othersIncDtls) {
							String natureDescNames = getNatureOfProfitInLieuOfSalaryDescName(
									othersIncDtl.getString("NatureDesc"));
							othersIncDtl.put("natureDescNames", natureDescNames);
//						System.out.println(natureDescNames);
						}
					}
					if (salarys.containsKey("NatureOfPerquisites")) {
						Document natureOfPerquisites = salarys.get("NatureOfPerquisites", Document.class);
						List<Document> othersIncDtls = natureOfPerquisites.getList("OthersIncDtls", Document.class,
								new ArrayList<>());
						for (Document othersIncDtl : othersIncDtls) {
							String natureDescNames = getPerquisitesDescName(othersIncDtl.getString("NatureDesc"));
							othersIncDtl.put("natureDescNames", natureDescNames);
//						System.out.println(natureDescNames);
						}
					}
				}

				if (scheduleS.containsKey("AllwncExemptUs10")) {
					Document allwncExemptUs10 = scheduleS.get("AllwncExemptUs10", Document.class);
					List<Document> allwncExemptUs10Dtls = allwncExemptUs10.getList("AllwncExemptUs10Dtls",
							Document.class, new ArrayList<>());
					for (Document allwncExemptUs10Dtl : allwncExemptUs10Dtls) {
						String salNatureDescNames = getSalNatureDescName(
								allwncExemptUs10Dtl.getString("SalNatureDesc"));
						allwncExemptUs10Dtl.put("salNatureDescNames", salNatureDescNames);
					}
				} // getSalNatureDescName
			}
			if (itrJson.containsKey("ScheduleVDA")) {
				Document scheduleVDA = itrJson.get("ScheduleVDA", Document.class);
				totalVda = scheduleVDA.getInteger("TotIncCapGain", 0) + scheduleVDA.getInteger("TotIncBusiness", 0);
			}

			if (itrJson.containsKey("ScheduleOS")) {
				Document scheduleOS = itrJson.get("ScheduleOS", Document.class);
				Document incOthThanOwnRaceHorse = scheduleOS.get("IncOthThanOwnRaceHorse", Document.class);
				if (incOthThanOwnRaceHorse != null) {
					Document deductions = incOthThanOwnRaceHorse.get("Deductions", Document.class);
					familyPensionIncome = incOthThanOwnRaceHorse.getInteger("FamilyPension", 0)
							- deductions.getInteger("DeductionUs57iia", 0);
					otherIncomeExcludefamilypension = incOthThanOwnRaceHorse.getInteger("AnyOtherIncome", 0)
							- incOthThanOwnRaceHorse.getInteger("FamilyPension", 0);
				}
			}

			if (itrJson.containsKey("ScheduleCGFor23")) {
				CapitalGainIncome capitalGainIncome = new CapitalGainIncome();
				Document scheduleCGFor23 = itrJson.get("ScheduleCGFor23", Document.class);
				if (scheduleCGFor23.containsKey("ShortTermCapGainFor23")) {
					List<ShortTermCapitalGain> shortTermCapitalGains = new ArrayList<>();
					List<ShortTermCapitalGainAt15Percent> shortTermCapitalGainAt15Percents = new ArrayList<>();
					Document shortTermCapGainFor23 = scheduleCGFor23.get("ShortTermCapGainFor23", Document.class);
					if (shortTermCapGainFor23.containsKey("SaleofLandBuild")) {
						Document saleofLandBuild = shortTermCapGainFor23.get("SaleofLandBuild", Document.class);
						if (saleofLandBuild.containsKey("SaleofLandBuildDtls")) {
							List<Document> saleofLandBuildDtls = saleofLandBuild.getList("SaleofLandBuildDtls",
									Document.class);
							for (Document saleOfLandBuild : saleofLandBuildDtls) {
								if (saleOfLandBuild.getInteger("AquisitCost", 0) != 0) {
									ShortTermCapitalGain shortTermCapitalGain = new ShortTermCapitalGain();
									shortTermCapitalGain.setNameOfTheAsset("Land and Building");
									shortTermCapitalGain.setNetSaleValue(
											BigDecimal.valueOf(saleOfLandBuild.getInteger("FullConsideration", 0)));
									shortTermCapitalGain.setPurchaseCost(
											BigDecimal.valueOf(saleOfLandBuild.getInteger("AquisitCost", 0)));
									shortTermCapitalGain.setCapitalGain(shortTermCapitalGain.getPurchaseCost()
											.subtract(shortTermCapitalGain.getNetSaleValue()));
									shortTermCapitalGain.setDeductions(
											BigDecimal.valueOf(saleOfLandBuild.getInteger("TotalDedn", 0)));
									shortTermCapitalGain.setNetCapitalGain(
											BigDecimal.valueOf(saleOfLandBuild.getInteger("STCGonImmvblPrprty", 0)));
									shortTermCapitalGains.add(shortTermCapitalGain);
								}
							}
						}
					}

					if (shortTermCapGainFor23.containsKey("SaleOnOtherAssets")) {
						Document saleOnOtherAssets = shortTermCapGainFor23.get("SaleOnOtherAssets", Document.class);
						if (saleOnOtherAssets.get("DeductSec48", Document.class).getInteger("AquisitCost", 0) != 0) {
							ShortTermCapitalGain shortTermCapitalGain = new ShortTermCapitalGain();
							shortTermCapitalGain.setNameOfTheAsset("Other Assets");
							shortTermCapitalGain.setNetSaleValue(
									BigDecimal.valueOf(saleOnOtherAssets.getInteger("FullConsideration", 0)));
							shortTermCapitalGain.setPurchaseCost(BigDecimal.valueOf(
									saleOnOtherAssets.get("DeductSec48", Document.class).getInteger("AquisitCost", 0)));
							shortTermCapitalGain.setCapitalGain(shortTermCapitalGain.getPurchaseCost()
									.subtract(shortTermCapitalGain.getNetSaleValue()));
							shortTermCapitalGain.setDeductions(BigDecimal.valueOf(
									saleOnOtherAssets.get("DeductSec48", Document.class).getInteger("TotalDedn", 0)
											- saleOnOtherAssets.get("DeductSec48", Document.class)
													.getInteger("AquisitCost", 0)));
							shortTermCapitalGain.setNetCapitalGain(
									BigDecimal.valueOf(saleOnOtherAssets.getInteger("CapgainonAssets", 0)));
							shortTermCapitalGains.add(shortTermCapitalGain);
						}
					}

					if (shortTermCapGainFor23.containsKey("EquityMFonSTT")) {
						List<Document> equityMFonSTT = shortTermCapGainFor23.getList("EquityMFonSTT", Document.class);
						equityMFonSTT.stream().forEach(stt -> {
							Document equityMFonSTTDtls = stt.get("EquityMFonSTTDtls", Document.class);
							if (equityMFonSTTDtls.get("DeductSec48", Document.class).getInteger("AquisitCost",
									0) != 0) {
								ShortTermCapitalGainAt15Percent shortTermCapitalGainAt15Percent = new ShortTermCapitalGainAt15Percent();
								shortTermCapitalGainAt15Percent.setNameOfTheAsset("Equity");
								shortTermCapitalGainAt15Percent.setPurchaseCost(BigDecimal.valueOf(equityMFonSTTDtls
										.get("DeductSec48", Document.class).getInteger("AquisitCost", 0)));
								shortTermCapitalGainAt15Percent.setNetSaleValue(
										BigDecimal.valueOf(equityMFonSTTDtls.getInteger("FullConsideration", 0)));
								shortTermCapitalGainAt15Percent
										.setCapitalGain((shortTermCapitalGainAt15Percent.getNetSaleValue()
												.subtract(shortTermCapitalGainAt15Percent.getPurchaseCost())));
								shortTermCapitalGainAt15Percent.setDeductions(BigDecimal.valueOf(
										equityMFonSTTDtls.get("DeductSec48", Document.class).getInteger("TotalDedn", 0)
												- equityMFonSTTDtls.get("DeductSec48", Document.class)
														.getInteger("AquisitCost", 0)));
								shortTermCapitalGainAt15Percent.setNetCapitalGain(shortTermCapitalGainAt15Percent
										.getCapitalGain().subtract(shortTermCapitalGainAt15Percent.getDeductions()));
								shortTermCapitalGainAt15Percents.add(shortTermCapitalGainAt15Percent);
							}
						});
					}

					if (!shortTermCapitalGainAt15Percents.isEmpty()) {
						capitalGainIncome.setShortTermCapitalGainAt15Percent(shortTermCapitalGainAt15Percents);
						capitalGainIncome.setShortTermCapitalGainAt15PercentTotal(
								BigDecimal.valueOf(shortTermCapitalGainAt15Percents.stream()
										.mapToDouble(cg -> cg.getNetCapitalGain().doubleValue()).sum()));
					} else
						capitalGainIncome.setShortTermCapitalGainAt15PercentTotal(BigDecimal.ZERO);

					if (!shortTermCapitalGains.isEmpty()) {
						capitalGainIncome.setShortTermCapitalGain(shortTermCapitalGains);
						capitalGainIncome.setShortTermCapitalGainTotal(BigDecimal.valueOf(shortTermCapitalGains.stream()
								.mapToDouble(cg -> cg.getNetCapitalGain().doubleValue()).sum()));
					} else
						capitalGainIncome.setShortTermCapitalGainTotal(BigDecimal.ZERO);
				}

				if (scheduleCGFor23.containsKey("LongTermCapGain23")) {
					Document longTermCapGain23 = scheduleCGFor23.get("LongTermCapGain23", Document.class);
					List<LongTermCapitalGainAt20Percent> longTermCapitalGainAt20Percents = new ArrayList<>();
					List<LongTermCapitalGainAt10Percent> longTermCapitalGainAt10Percents = new ArrayList<>();
					if (longTermCapGain23.containsKey("SaleofLandBuild")) {
						Document saleofLandBuild = longTermCapGain23.get("SaleofLandBuild", Document.class);
						if (saleofLandBuild.containsKey("SaleofLandBuildDtls")) {
							List<Document> saleofLandBuildDtls = saleofLandBuild.getList("SaleofLandBuildDtls",
									Document.class);
							for (Document saleofLandBuildDtl : saleofLandBuildDtls) {
								if (saleofLandBuildDtl.getInteger("AquisitCostIndex", 0) != 0) {
									LongTermCapitalGainAt20Percent longTermCapitalGainAt20Percent = new LongTermCapitalGainAt20Percent();
									longTermCapitalGainAt20Percent.setNameOfTheAsset("Land and Building");
									longTermCapitalGainAt20Percent.setPurchaseCost(
											BigDecimal.valueOf(saleofLandBuildDtl.getInteger("AquisitCostIndex", 0)));
									longTermCapitalGainAt20Percent.setNetSaleValue(
											BigDecimal.valueOf(saleofLandBuildDtl.getInteger("FullConsideration", 0)));
									longTermCapitalGainAt20Percent
											.setCapitalGain(longTermCapitalGainAt20Percent.getNetSaleValue()
													.subtract(longTermCapitalGainAt20Percent.getPurchaseCost()));
									longTermCapitalGainAt20Percent.setDeductions(
											BigDecimal.valueOf(saleofLandBuildDtl.getInteger("TotalDedn", 0))
													.subtract(longTermCapitalGainAt20Percent.getPurchaseCost()));
									longTermCapitalGainAt20Percent.setNetCapitalGain(longTermCapitalGainAt20Percent
											.getCapitalGain().subtract(longTermCapitalGainAt20Percent.getDeductions()));
									longTermCapitalGainAt20Percents.add(longTermCapitalGainAt20Percent);
								}
							}
						}
					}

					if (longTermCapGain23.containsKey("SaleofBondsDebntr")) {
						Document saleofBondsDebntr = longTermCapGain23.get("SaleofBondsDebntr", Document.class);
						if (saleofBondsDebntr.get("DeductSec48", Document.class).getInteger("AquisitCost", 0) != 0) {
							LongTermCapitalGainAt20Percent longTermCapitalGainAt20Percent = new LongTermCapitalGainAt20Percent();
							longTermCapitalGainAt20Percent.setNameOfTheAsset("Bonds");
							longTermCapitalGainAt20Percent.setPurchaseCost(BigDecimal.valueOf(
									saleofBondsDebntr.get("DeductSec48", Document.class).getInteger("AquisitCost", 0)));
							longTermCapitalGainAt20Percent.setNetSaleValue(
									BigDecimal.valueOf(saleofBondsDebntr.getInteger("FullConsideration", 0)));
							longTermCapitalGainAt20Percent.setCapitalGain(longTermCapitalGainAt20Percent
									.getNetSaleValue().subtract(longTermCapitalGainAt20Percent.getPurchaseCost()));
							longTermCapitalGainAt20Percent.setDeductions(BigDecimal
									.valueOf(saleofBondsDebntr.get("DeductSec48", Document.class)
											.getInteger("TotalDedn", 0))
									.subtract(longTermCapitalGainAt20Percent.getPurchaseCost()));
							longTermCapitalGainAt20Percent.setNetCapitalGain(longTermCapitalGainAt20Percent
									.getCapitalGain().subtract(longTermCapitalGainAt20Percent.getDeductions()));
							longTermCapitalGainAt20Percents.add(longTermCapitalGainAt20Percent);
						}
					}

					if (longTermCapGain23.containsKey("SaleofAssetNA")) {
						Document saleofAssetNA = longTermCapGain23.get("SaleofAssetNA", Document.class);
						if (saleofAssetNA.containsKey("DeductSec48")
								&& saleofAssetNA.get("DeductSec48", Document.class).getInteger("AquisitCost", 0) != 0) {
							LongTermCapitalGainAt20Percent longTermCapitalGainAt20Percent = new LongTermCapitalGainAt20Percent();
							longTermCapitalGainAt20Percent.setNameOfTheAsset("Other Asset");
							longTermCapitalGainAt20Percent.setPurchaseCost(BigDecimal.valueOf(
									saleofAssetNA.get("DeductSec48", Document.class).getInteger("AquisitCost", 0)));
							longTermCapitalGainAt20Percent.setNetSaleValue(
									BigDecimal.valueOf(saleofAssetNA.getInteger("FullConsideration", 0)));
							longTermCapitalGainAt20Percent.setCapitalGain(longTermCapitalGainAt20Percent
									.getNetSaleValue().subtract(longTermCapitalGainAt20Percent.getPurchaseCost()));
							longTermCapitalGainAt20Percent.setDeductions(BigDecimal
									.valueOf(
											saleofAssetNA.get("DeductSec48", Document.class).getInteger("TotalDedn", 0))
									.subtract(longTermCapitalGainAt20Percent.getPurchaseCost()));
							longTermCapitalGainAt20Percent.setNetCapitalGain(longTermCapitalGainAt20Percent
									.getCapitalGain().subtract(longTermCapitalGainAt20Percent.getDeductions()));
							longTermCapitalGainAt20Percents.add(longTermCapitalGainAt20Percent);
						}
					}

					if (itrJson.containsKey("Schedule112A")) {
						Document schedule112A = itrJson.get("Schedule112A", Document.class);
						if (schedule112A.containsKey("Schedule112ADtls")) {
							List<Document> schedule112ADtls = schedule112A.getList("Schedule112ADtls", Document.class);
							for (Document schedule112ADtl : schedule112ADtls) {
								LongTermCapitalGainAt10Percent longTermCapitalGainAt10Percent = new LongTermCapitalGainAt10Percent();
								longTermCapitalGainAt10Percent.setNameOfTheAsset("Equity");
								longTermCapitalGainAt10Percent.setPurchaseCost(
										BigDecimal.valueOf(schedule112ADtl.getInteger("CostAcqWithoutIndx", 0)));
								longTermCapitalGainAt10Percent
										.setNetSaleValue(BigDecimal.valueOf(schedule112ADtl.getInteger("Balance", 0)
												+ schedule112ADtl.getInteger("TotalDeductions", 0)));
								longTermCapitalGainAt10Percent.setCapitalGain(longTermCapitalGainAt10Percent
										.getNetSaleValue().subtract(longTermCapitalGainAt10Percent.getPurchaseCost()));
								longTermCapitalGainAt10Percent.setDeductions(
										BigDecimal.valueOf(schedule112ADtl.getInteger("TotalDeductions", 0))
												.subtract(longTermCapitalGainAt10Percent.getPurchaseCost()));
								longTermCapitalGainAt10Percent.setNetCapitalGain(longTermCapitalGainAt10Percent
										.getCapitalGain().subtract(longTermCapitalGainAt10Percent.getDeductions()));
								longTermCapitalGainAt10Percents.add(longTermCapitalGainAt10Percent);
							}
						}
					}

					if (!longTermCapitalGainAt10Percents.isEmpty()) {
						capitalGainIncome.setLongTermCapitalGainAt10Percent(longTermCapitalGainAt10Percents);
						capitalGainIncome.setLongTermCapitalGainAt10PercentTotal(
								BigDecimal.valueOf(longTermCapitalGainAt10Percents.stream()
										.mapToDouble(cg -> cg.getNetCapitalGain().doubleValue()).sum()));
					} else
						capitalGainIncome.setLongTermCapitalGainAt10PercentTotal(BigDecimal.ZERO);

					if (!longTermCapitalGainAt20Percents.isEmpty()) {
						capitalGainIncome.setLongTermCapitalGainAt20Percent(longTermCapitalGainAt20Percents);
						capitalGainIncome.setLongTermCapitalGainAt20PercentTotal(
								BigDecimal.valueOf(longTermCapitalGainAt20Percents.stream()
										.mapToDouble(cg -> cg.getNetCapitalGain().doubleValue()).sum()));
					} else
						capitalGainIncome.setLongTermCapitalGainAt20PercentTotal(BigDecimal.ZERO);
				}

				itrJson.put("capitalGainIncome",
						Document.parse(new ObjectMapper().writeValueAsString(capitalGainIncome)));
			}

			if (ItrConstants.ITR_TYPE_3.equals(itr.getItrType())) {
				if (itrJson.containsKey("PARTA_PL")) {
					Document partAPL = itrJson.get("PARTA_PL", Document.class);
					int grossProfitTrnsfFrmTrdAcc = 0;
					int pbt = 0;
					if (partAPL.containsKey("CreditsToPL")) {
						Document creditsToPL = partAPL.get("CreditsToPL", Document.class);
						if (creditsToPL.containsKey("GrossProfitTrnsfFrmTrdAcc")) {
							grossProfitTrnsfFrmTrdAcc = creditsToPL.getInteger("GrossProfitTrnsfFrmTrdAcc", 0);

						}
					}

					if (partAPL.containsKey("DebitsToPL")) {
						Document debitsToPL = partAPL.get("DebitsToPL", Document.class);
						pbt = debitsToPL.getInteger("PBT", 0);
					}

					tradingExpenses = grossProfitTrnsfFrmTrdAcc - pbt;

					if (itrJson.containsKey("TradingAccount")) {
						Document tradingAccount = itrJson.get("TradingAccount", Document.class);
						tradingExpenses += tradingAccount.getInteger("DirectExpenses", 0);
					}

					Document itrMaster = mongoTemplate.findAll(Document.class, "itrMaster").get(0);

					List<Document> natureOfBusinesses = itrMaster.getList("natureOfBusiness", Document.class);

					if (partAPL.containsKey("NatOfBus44AD")) {
						List<Document> natOfBus44ADList = partAPL.getList("NatOfBus44AD", Document.class);
						for (Document natOfBus44AD : natOfBus44ADList) {
							Optional<Document> natureOfBusiness = natureOfBusinesses.stream()
									.filter(nob -> nob.getString("type").equals("BUSINESS")
											&& nob.getString("code").equals(natOfBus44AD.getString("CodeAD")))
									.findAny();
							if (natureOfBusiness.isPresent())
								natOfBus44AD.put("natureOfBusiness", natureOfBusiness.get().getString("label"));
						}
					}

					if (partAPL.containsKey("NatOfBus44ADA")) {
						List<Document> natOfBus44ADList = partAPL.getList("NatOfBus44ADA", Document.class);
						for (Document natOfBus44AD : natOfBus44ADList) {
							Optional<Document> natureOfBusiness = natureOfBusinesses.stream()
									.filter(nob -> nob.getString("type").equals("PROFESSION")
											&& nob.getString("code").equals(natOfBus44AD.getString("CodeADA")))
									.findAny();
							if (natureOfBusiness.isPresent())
								natOfBus44AD.put("natureOfBusiness", natureOfBusiness.get().getString("label"));
						}
					}
				}

				if (itrJson.containsKey("PARTA_PL")) {
					Document partPL = itrJson.get("PARTA_PL", Document.class);
					if (partPL != null) {
						partPLNatOf44AD = partPL.getList("NatOfBus44AD", Document.class);
						partPLNatOf44ADA = partPL.getList("NatOfBus44ADA", Document.class);

						if (partPL.containsKey("PersumptiveInc44AD")) {

							Document persumptiveInc44AD = partPL.get("PersumptiveInc44AD", Document.class);
							if (persumptiveInc44AD != null) {
								grsTrnOverBank = persumptiveInc44AD.getInteger("GrsTrnOverBank", 0);
								int grsTrnOverAnyOthMode = persumptiveInc44AD.getInteger("GrsTrnOverAnyOthMode", 0);
								int grsTotalTrnOverInCash = persumptiveInc44AD.getInteger("GrsTotalTrnOverInCash", 0);
								grsTotalTrnOverInCashAndOtherInc = grsTotalTrnOverInCash + grsTrnOverAnyOthMode;
								persumptiveInc44AD6Per = persumptiveInc44AD.getInteger("PersumptiveInc44AD6Per", 0);
								persumptiveInc44AD8Per = persumptiveInc44AD.getInteger("PersumptiveInc44AD8Per", 0);
								grsTrnOverOrReceipt = persumptiveInc44AD.getInteger("GrsTrnOverOrReceipt", 0);
								totPersumptiveInc44AD = persumptiveInc44AD.getInteger("TotPersumptiveInc44AD", 0);
							}
						}
						if (partPL.containsKey("PersumptiveInc44ADA")) {

							Document persumptiveInc44ADA = partPL.get("PersumptiveInc44ADA", Document.class);
							if (persumptiveInc44ADA != null) {
								grsReceiptITR3 = persumptiveInc44ADA.getInteger("GrsReceipt", 0);
								totPersumptiveInc44ADA = persumptiveInc44ADA.getInteger("TotPersumptiveInc44ADA", 0);
							}
						}
					}
				}

				if (itrJson.containsKey("PARTA_BS")) {
					Document partABS = itrJson.get("PARTA_BS", Document.class);
					if (partABS.containsKey("FundSrc")) {
						Document fundSrc = partABS.get("FundSrc", Document.class);
						if (fundSrc.containsKey("PropFund")) {
							Document propFund = fundSrc.get("PropFund", Document.class);
							totalLiabilities += propFund.getInteger("TotPropFund", 0);
						}

						if (fundSrc.containsKey("LoanFunds")) {
							Document loanFunds = fundSrc.get("LoanFunds", Document.class);
							totalLiabilities += loanFunds.getInteger("TotLoanFund", 0);
						}

						if (fundSrc.containsKey("Advances")) {
							Document advances = fundSrc.get("Advances", Document.class);
							totalLiabilities += advances.getInteger("TotalAdvances", 0);
						}

					}

					if (partABS.containsKey("FundApply")) {
						Document fundApply = partABS.get("FundApply", Document.class);
						if (fundApply.containsKey("CurrLiabilitiesProv")) {
							Document currLiabilitiesProv = fundApply.get("CurrLiabilitiesProv", Document.class);
							if (currLiabilitiesProv.containsKey("CurrLiabilities")) {
								Document currLiabilities = currLiabilitiesProv.get("CurrLiabilities", Document.class);
								otherLiabilities = currLiabilities.getInteger("TotCurrLiabilities", 0)
										- currLiabilities.getInteger("SundryCred", 0);
								totalLiabilities += currLiabilities.getInteger("TotCurrLiabilities", 0);
							}
						}

						if (fundApply.containsKey("FixedAsset")) {
							Document fixedAsset = fundApply.get("FixedAsset", Document.class);
							totalAssets += fixedAsset.getInteger("TotFixedAsset", 0);
						}
						if (fundApply.containsKey("Investments")) {
							Document investments = fundApply.get("Investments", Document.class);
							totalAssets += investments.getInteger("TotInvestments", 0);
						}
						if (fundApply.containsKey("CurrAssetLoanAdv")) {
							Document currAssetLoanAdv = fundApply.get("CurrAssetLoanAdv", Document.class);
							if (currAssetLoanAdv.containsKey("CurrAsset")) {

								Document currAsset = currAssetLoanAdv.get("CurrAsset", Document.class);

								if (currAsset.containsKey("Inventories")) {
									Document inventories = currAsset.get("Inventories", Document.class);
									totalAssets += inventories.getInteger("TotInventries", 0);
								}

								if (currAsset.containsKey("CashOrBankBal")) {
									Document cashOrBankBal = currAsset.get("CashOrBankBal", Document.class);
									totalAssets += cashOrBankBal.getInteger("TotCashOrBankBal", 0);
								}

								totalAssets += currAsset.getInteger("SndryDebtors", 0)
										+ currAsset.getInteger("OthCurrAsset", 0);
							}
							if (currAssetLoanAdv.containsKey("LoanAdv")) {
								Document loanAdv = currAssetLoanAdv.get("LoanAdv", Document.class);
								totalAssets += loanAdv.getInteger("TotLoanAdv", 0);
							}
							if (currAssetLoanAdv.containsKey("CurrLiabilitiesProv")) {
								Document currLiabilitiesProv = currAssetLoanAdv.get("CurrLiabilitiesProv",
										Document.class);
								totalLiabilities += currLiabilitiesProv.getInteger("TotCurrLiabilitiesProvision", 0);
							}

						}

					}
				}

			}
		}

		if (isITRU) {
			String assessmentYear = itrJson.get("PartA_139_8A", Document.class).getString("AssessmentYear");
			itrJson.put("assessmentYear", assessmentYear + "-" + (Integer.parseInt(assessmentYear) + 1));
			itrJson.put("isITRU", isITRU);
			Document partBATI = itrJson.get("PartB-ATI", Document.class);
			itrJson.put("additionalIncomeTax", partBATI.getInteger("AddtnlIncTax", 0));
			itrJson.put("netIncomeTaxLiability", partBATI.getInteger("NetPayable", 0));
			itrJson.put("taxPaidUs140B", partBATI.getInteger("TaxUS140B", 0));
		}

		Document schedule80D = itrJson.get("Schedule80D", Document.class);
		if (schedule80D != null) {
			Document sec80DSelfFamSrCtznHealth = schedule80D.get("Sec80DSelfFamSrCtznHealth", Document.class);
			preventiveHealthCheckupForWholeFamily = sec80DSelfFamSrCtznHealth.getInteger("PrevHlthChckUpSlfFam", 0)
					+ sec80DSelfFamSrCtznHealth.getInteger("PrevHlthChckUpSlfFamSrCtzn", 0)
					+ sec80DSelfFamSrCtznHealth.getInteger("PrevHlthChckUpParentsSrCtzn", 0);
			medicalExpenditure = sec80DSelfFamSrCtznHealth.getInteger("MedicalExpSlfFamSrCtzn", 0)
					+ sec80DSelfFamSrCtznHealth.getInteger("MedicalExpParentsSrCtzn", 0);
		}

		int debitsToPLTotal = 0;
		int employeeCompTotal = 0;
		int insurancesTotal = 0;
		int commissionExpdrDtlsTotal = 0;
		int royalityDtlsTotal = 0;
		int professionalConstDtlsTotal = 0;
		int totExciseCustomsVAT = 0;
		int badDebtTotal = 0;
		int interestExpdrTotal = 0;

		Document partaPL = itrJson.get("PARTA_PL", Document.class);
		if (partaPL != null) {
			Document debitsToPL = partaPL.get("DebitsToPL", Document.class);
			if (debitsToPL != null) {
				Document employeeComp = debitsToPL.get("EmployeeComp", Document.class);
				Document insurances = debitsToPL.get("Insurances", Document.class);
				Document commissionExpdrDtls = debitsToPL.get("CommissionExpdrDtls", Document.class);
				Document royalityDtls = debitsToPL.get("RoyalityDtls", Document.class);
				Document professionalConstDtls = debitsToPL.get("ProfessionalConstDtls", Document.class);
				Document ratesTaxesPays = debitsToPL.get("RatesTaxesPays", Document.class);
				Document exciseCustomsVAT = ratesTaxesPays.get("ExciseCustomsVAT", Document.class);
				Document badDebtDtls = debitsToPL.get("BadDebtDtls", Document.class);
				Document interestExpdrtDtls = debitsToPL.get("InterestExpdrtDtls", Document.class);

				if (employeeComp != null) {
					employeeCompTotal = employeeComp.getInteger("TotEmployeeComp", 0);
				}
				if (insurances != null) {
					insurancesTotal = insurances.getInteger("TotInsurances", 0);
				}
				if (commissionExpdrDtls != null) {
					commissionExpdrDtlsTotal = commissionExpdrDtls.getInteger("Total", 0);
				}
				if (royalityDtls != null) {
					royalityDtlsTotal = royalityDtls.getInteger("Total", 0);
				}
				if (professionalConstDtls != null) {
					professionalConstDtlsTotal = professionalConstDtls.getInteger("Total", 0);
				}
				if (exciseCustomsVAT != null) {
					totExciseCustomsVAT = exciseCustomsVAT.getInteger("TotExciseCustomsVAT", 0);
				}
				if (badDebtDtls != null) {
					badDebtTotal = badDebtDtls.getInteger("BadDebt", 0);
				}
				if (interestExpdrtDtls != null) {
					interestExpdrTotal = interestExpdrtDtls.getInteger("InterestExpdr", 0);
				}

				debitsToPLTotal = employeeCompTotal + insurancesTotal + commissionExpdrDtlsTotal + royalityDtlsTotal
						+ professionalConstDtlsTotal + totExciseCustomsVAT + badDebtTotal + interestExpdrTotal
						+ debitsToPL.getInteger("Freight", 0) + debitsToPL.getInteger("ConsumptionOfStores", 0)
						+ debitsToPL.getInteger("PowerFuel", 0) + debitsToPL.getInteger("RentExpdr", 0)
						+ debitsToPL.getInteger("RepairsBldg", 0) + debitsToPL.getInteger("RepairMach", 0)
						+ debitsToPL.getInteger("StaffWelfareExp", 0) + debitsToPL.getInteger("Entertainment", 0)
						+ debitsToPL.getInteger("Hospitality", 0) + debitsToPL.getInteger("Conference", 0)
						+ debitsToPL.getInteger("SalePromoExp", 0) + debitsToPL.getInteger("Advertisement", 0)
						+ debitsToPL.getInteger("HotelBoardLodge", 0) + debitsToPL.getInteger("TravelExp", 0)
						+ debitsToPL.getInteger("ForeignTravelExp", 0) + debitsToPL.getInteger("ConveyanceExp", 0)
						+ debitsToPL.getInteger("TelephoneExp", 0) + debitsToPL.getInteger("GuestHouseExp", 0)
						+ debitsToPL.getInteger("ClubExp", 0) + debitsToPL.getInteger("FestivalCelebExp", 0)
						+ debitsToPL.getInteger("Scholarship", 0) + debitsToPL.getInteger("Gift", 0)
						+ debitsToPL.getInteger("Donation", 0) + debitsToPL.getInteger("AuditFee", 0)
						+ debitsToPL.getInteger("OtherExpenses", 0) + debitsToPL.getInteger("ProvForBadDoubtDebt", 0)
						+ debitsToPL.getInteger("OthProvisionsExpdr", 0)
						+ debitsToPL.getInteger("DepreciationAmort", 0);
			}
		}
		
		List<Map<String, Object>> taxCalculations = new ArrayList<>();
		
		int srNo = 0;
		
		if (taxPayableOnNormalIncome > 0) {
			org.bson.Document salaryIncome = new org.bson.Document();
			salaryIncome.put("srNo", ++srNo);
			salaryIncome.put("incomeType", "Normal Income");
			salaryIncome.put("grossIncome", totalNormalGrossIncome);
			salaryIncome.put("taxableIncome", totalNormalTaxableIncome);
			salaryIncome.put("rate", "Slab Rate");
			salaryIncome.put("tax", taxPayableOnNormalIncome);
			taxCalculations.add(salaryIncome);
		}
		
		if (StringUtils.equalsAny(itr.getItrType(), "2", "3")) {
			Document scheduleSI = itrJson.get("ScheduleSI", Document.class);
			List<Document> splCodeRateTax = scheduleSI != null ? scheduleSI.getList("SplCodeRateTax", Document.class) : new ArrayList<>();

			int cgIncomeAt15 = 0;
			Optional<Document> secCode1A = splCodeRateTax.stream().filter(spl -> spl.getString("SecCode").equals("1A"))
					.findAny();
			if (secCode1A.isPresent())
				cgIncomeAt15 = secCode1A.get().getInteger("SplRateInc", 0);

			int sumOfEquityGain = 0;
			Optional<Document> secCode2A = splCodeRateTax.stream().filter(spl -> spl.getString("SecCode").equals("2A"))
					.findAny();
			if (secCode2A.isPresent())
				sumOfEquityGain = secCode2A.get().getInteger("SplRateInc", 0);

			int cgIncomeAt20 = 0;
			Optional<Document> secCode21 = splCodeRateTax.stream().filter(spl -> spl.getString("SecCode").equals("21"))
					.findAny();
			if (secCode21.isPresent())
				cgIncomeAt20 = secCode21.get().getInteger("SplRateInc", 0);

			int cgIncomeAt10 = 0;
			Optional<Document> secCode22 = splCodeRateTax.stream().filter(spl -> spl.getString("SecCode").equals("22"))
					.findAny();
			if (secCode22.isPresent())
				cgIncomeAt10 = secCode22.get().getInteger("SplRateInc", 0);

			if (cgIncomeAt15 > 0 || sumOfEquityGain > 0 || cgIncomeAt20 > 0 || cgIncomeAt10 > 0) {
				org.bson.Document cg = new org.bson.Document();
				cg.put("srNo", ++srNo);
				cg.put("incomeType", "Capital Gain");
				taxCalculations.add(cg);
			}

			if (cgIncomeAt15 > 0) {
				org.bson.Document cg111A = new org.bson.Document();
				cg111A.put("incomeType", "- 111A");
				cg111A.put("grossIncome", cgIncomeAt15);
				cg111A.put("taxableIncome", cgIncomeAt15);
				cg111A.put("rate", "15%");
				cg111A.put("tax", secCode1A.get().getInteger("SplRateIncTax", 0));
				taxCalculations.add(cg111A);
			}

			double exemption112A = 100000;

			double cgIncome112A = sumOfEquityGain;

			if (sumOfEquityGain > exemption112A)
				cgIncome112A = sumOfEquityGain - exemption112A;

			if (sumOfEquityGain > 0) {
				org.bson.Document cg112A = new org.bson.Document();
				cg112A.put("incomeType", "- 112A");
				cg112A.put("grossIncome", sumOfEquityGain);
				cg112A.put("taxableIncome", cgIncome112A);
				cg112A.put("rate", "10%");
				cg112A.put("tax", secCode2A.get().getInteger("SplRateIncTax", 0));
				taxCalculations.add(cg112A);
			}

			if (cgIncomeAt20 > 0) {
				org.bson.Document cgAt20LongTerm = new org.bson.Document();
				cgAt20LongTerm.put("incomeType", "Other Long Term Assets(including Land nad Building)");
				cgAt20LongTerm.put("grossIncome", cgIncomeAt20);
				cgAt20LongTerm.put("taxableIncome", cgIncomeAt20);
				cgAt20LongTerm.put("rate", "20%");
				cgAt20LongTerm.put("tax", secCode21.get().getInteger("SplRateIncTax", 0));
				taxCalculations.add(cgAt20LongTerm);
			}

			if (cgIncomeAt10 > 0) {
				org.bson.Document cgAt10LongTerm = new org.bson.Document();
				cgAt10LongTerm.put("incomeType", "ZCB, Listed Bonds and Debentures");
				cgAt10LongTerm.put("grossIncome", cgIncomeAt10);
				cgAt10LongTerm.put("taxableIncome", cgIncomeAt10);
				cgAt10LongTerm.put("rate", "10%");
				cgAt10LongTerm.put("tax", secCode22.get().getInteger("SplRateIncTax", 0));
				taxCalculations.add(cgAt10LongTerm);
			}

			int winningIncome = 0;
			Optional<Document> secCodebb = splCodeRateTax.stream()
					.filter(spl -> spl.getString("SecCode").equals("5BBJ")).findAny();
			Optional<Document> secCodebbj = splCodeRateTax.stream()
					.filter(spl -> spl.getString("SecCode").equals("5BB")).findAny();
			if (secCodebb.isPresent())
				winningIncome += secCodebb.get().getInteger("SplRateInc", 0);

			if (secCodebbj.isPresent())
				winningIncome += secCodebbj.get().getInteger("SplRateInc", 0);

			if (winningIncome > 0) {
				org.bson.Document winnnigLotteryIncome = new org.bson.Document();
				winnnigLotteryIncome.put("srNo", ++srNo);
				winnnigLotteryIncome.put("incomeType", "Winning/ Lottery/ Crossward Puzzle");
				winnnigLotteryIncome.put("grossIncome", winningIncome);
				winnnigLotteryIncome.put("taxableIncome", winningIncome);
				winnnigLotteryIncome.put("rate", "30%");
				int winningIncomeTax = 0;
				if (secCodebb.isPresent())
					winningIncomeTax += secCodebb.get().getInteger("SplRateIncTax", 0);

				if (secCodebbj.isPresent())
					winningIncomeTax += secCodebbj.get().getInteger("SplRateIncTax", 0);

				winnnigLotteryIncome.put("tax", winningIncomeTax);
				taxCalculations.add(winnnigLotteryIncome);
			}

			int vdaIncome = 0;
			Optional<Document> secCodebbhbp = splCodeRateTax.stream()
					.filter(spl -> spl.getString("SecCode").equals("5BBH_BP")).findAny();
			Optional<Document> secCodebbh = splCodeRateTax.stream()
					.filter(spl -> spl.getString("SecCode").equals("5BBH")).findAny();
			if (secCodebbhbp.isPresent())
				vdaIncome += secCodebbhbp.get().getInteger("SplRateInc", 0);

			if (secCodebbh.isPresent())
				vdaIncome += secCodebbh.get().getInteger("SplRateInc", 0);

			if (vdaIncome > 0) {
				org.bson.Document vda = new org.bson.Document();
				vda.put("srNo", ++srNo);
				vda.put("incomeType", "VDA");
				vda.put("grossIncome", vdaIncome);
				vda.put("taxableIncome", vdaIncome);
				vda.put("rate", "30%");
				int vdaIncomeTax = 0;
				if (secCodebbhbp.isPresent())
					vdaIncomeTax += secCodebbhbp.get().getInteger("SplRateIncTax", 0);

				if (secCodebbh.isPresent())
					vdaIncomeTax += secCodebbh.get().getInteger("SplRateIncTax", 0);

				vda.put("tax", vdaIncomeTax);
				taxCalculations.add(vda);
			}

		}
		if(!taxCalculations.isEmpty()) {
			itrJson.put("showTaxCalculation", "Y");
			itrJson.put("taxCalculations", taxCalculations);
		}
		
		itrJson.put("debitsToPLTotal", debitsToPLTotal);
		itrJson.put("familyPension", familyPensionIncome);
		itrJson.put("otherIncomeExcludefamilypension", otherIncomeExcludefamilypension);
		itrJson.put("totalVDACapitalGain", totalVDACapitalGain);
		itrJson.put("totalVDABusinessIncome", totalVDABusinessIncome);
		itrJson.put("movableAssetTotal", movableAssetTotal);
		itrJson.put("immovableAssetTotal", immovableAssetTotal);
		itrJson.put("totalVda", totalVda);
		itrJson.put("otherLiabilities", otherLiabilities);
		itrJson.put("totalLiabilities", totalLiabilities);
		itrJson.put("totalAssets", totalAssets);
		itrJson.put("tradingExpenses", tradingExpenses);
		itrJson.put("otherAllowance", otherAllowance);
		itrJson.put("houseRentAllowance", houseRentAllowance);
		itrJson.put("anyOtherIncome", anyOtherIncome);
		itrJson.put("longTermCapitalGainAt20PercentTotal", longTermCapitalGainAt20PercentTotal);
		itrJson.put("longTermCapitalGainAt10PercentTotal", longTermCapitalGainAt10PercentTotal);
		itrJson.put("shortTermCapitalGainAt15PercentTotal", shortTermCapitalGainAt15PercentTotal);
		itrJson.put("shortTermCapitalGainTotal", shortTermCapitalGainTotal);
		itrJson.put("lossesSetOffDuringTheYear", lossesSetOffDuringTheYear);
		itrJson.put("grossTrunover44AD", grossTrunover44AD);
		itrJson.put("totalIntrestPay", totalIntrestPay);
		itrJson.put("medicalExpenditure", medicalExpenditure);
		itrJson.put("preventiveHealthCheckupForWholeFamily", preventiveHealthCheckupForWholeFamily);
		if (!donations.isEmpty())
			itrJson.put("donations", donations);
		itrJson.put("sumOfUs80cUs80cccUs80ccc1", sumOfUs80cUs80cccUs80ccc1);
		itrJson.put("sumOfUs80ggaUs80ggc", sumOfUs80ggaUs80ggc);
		itrJson.put("us80ttaTtb", us80ttaTtb);
		itrJson.put("grsTrnOverBank", grsTrnOverBank);
		itrJson.put("grsTotalTrnOverInCashAndOtherInc", grsTotalTrnOverInCashAndOtherInc);
		itrJson.put("persumptiveIncSixPer", persumptiveInc44AD6Per);
		itrJson.put("persumptiveIncEightPer", persumptiveInc44AD8Per);
		itrJson.put("totPersumptiveIncFortyFourAD", totPersumptiveInc44AD);
		itrJson.put("grsTrnOverOrReceipt", grsTrnOverOrReceipt);
		itrJson.put("partPLNatOfFourtyFourAD", partPLNatOf44AD);
		itrJson.put("partPLNatOfFourtyFourADA", partPLNatOf44ADA);
		itrJson.put("grsReceiptITR3", grsReceiptITR3);
		itrJson.put("totPersumptiveIncFortyFourADA", totPersumptiveInc44ADA);
		itrJson.put("grsTrnOverBankItrFour", grsTrnOverBankItrFour);
		itrJson.put("grsTotalCashAndOtherIncITRFour", grsTotalCashAndOtherIncITRFour);
		itrJson.put("grsTrnOverOrReceiptItrFour", grsTrnOverOrReceiptItrFour);
		itrJson.put("persumptiveIncSixPerItrFour", persumptiveInc44AD6PerItrFour);
		itrJson.put("persumptiveIncEightPerItrFour", persumptiveInc44AD8PerItrFour);
		itrJson.put("totPersumptiveIncFortyFourADItrFour", totPersumptiveInc44ADItrFour);
		itrJson.put("grsReceiptITR4", grsReceiptITR4);
		itrJson.put("totPersumptiveIncFortyFourADAITR4", totPersumptiveInc44ADAITR4);
		itrJson.put("itrFourPartPLNatOfFortyFourADA", itrFourPartPLNatOf44ADA);
		itrJson.put("itrFourPartPLNatOfFortyFourAD", itrFourPartPLNatOf44AD);
		itrJson.put("totalProfBusGainIncomeITR3", totalProfBusGainIncomeITR3);

		String itrSummaryJsonStr = summaryJson.toJson();
		Map<String, String> replaceMap = Map.of("115BBH", "BBH115", "44AD", "AD44", "44ADA", "ADA44", "44AE", "AE44",
				"44BB", "BB44");
		for (Entry<String, String> entry : replaceMap.entrySet())
			itrSummaryJsonStr = itrSummaryJsonStr.replace(entry.getKey(), entry.getValue());

		JSONObject itrSummaryJsonV2 = new JSONObject(itrSummaryJsonStr);
		return XML.toString(itrSummaryJsonV2);
	}
	private static BigDecimal extractPercentage(String assetGroup) {
	    String[] parts = assetGroup.split(" ");
	    for (String part : parts) {
	        try {
	            return new BigDecimal(part.replace("%", ""));
	        } catch (NumberFormatException ignored) {
	        }
	    }
	    return BigDecimal.ZERO; 
	}

	public static List<DepreciationDetailsDTO> getDepreciationDetails(Map<String, Object> scheduleData) {
	    Map<String, DepreciationDetailsDTO> assetMap = new HashMap<>();
	    Map<String, Object> scheduleDOA = (Map<String, Object>) scheduleData.get("ScheduleDOA");
	    Map<String, Object> scheduleDPM = (Map<String, Object>) scheduleData.get("ScheduleDPM");

	    processScheduleData(scheduleDOA, assetMap);
	    processScheduleData(scheduleDPM, assetMap);

	    return new ArrayList<>(assetMap.values());
	}

	private static void processScheduleData(Map<String, Object> schedule, Map<String, DepreciationDetailsDTO> assetMap) {
	    if (schedule == null) return;

	    for (String assetGroup : schedule.keySet()) {
	        Object rateDataObj = schedule.get(assetGroup);
	        if (!(rateDataObj instanceof Map)) continue;

	        Map<String, Object> rateDataMap = (Map<String, Object>) rateDataObj;

	        for (String rateKey : rateDataMap.keySet()) {
	            Object depreciationDetailsObj = rateDataMap.get(rateKey);
	            if (!(depreciationDetailsObj instanceof Map)) continue;

	            Map<String, Object> depreciationDetailsMap = (Map<String, Object>) depreciationDetailsObj;
	            Map<String, Object> depreciationDetailMap = (Map<String, Object>) depreciationDetailsMap.get("DepreciationDetail");

	            if (depreciationDetailMap == null && !"Land".equals(assetGroup)) continue;

	            String newAssetGroup = determineAssetGroupLabel(assetGroup, rateKey);
	            DepreciationDetailsDTO depreciationDTO = assetMap.getOrDefault(newAssetGroup, new DepreciationDetailsDTO());
	            depreciationDTO.setAssetGroup(newAssetGroup);

	            BigDecimal wdvFirstDay = BigDecimal.ZERO;
	            BigDecimal additionsGrThan180Days = BigDecimal.ZERO;
	            BigDecimal additionsLessThan180Days = BigDecimal.ZERO;
	            BigDecimal realizationTotalPeriod = BigDecimal.ZERO;
	            BigDecimal realizationPeriodLessThan180Days = BigDecimal.ZERO;
	            BigDecimal netAggregateDepreciation = BigDecimal.ZERO;
	            BigDecimal wdvLastDay = BigDecimal.ZERO;

	            if ("Land".equals(assetGroup)) {
	                wdvFirstDay = parseBigDecimal(depreciationDetailsMap.get("WDVFirstDay"));
	                additionsGrThan180Days = parseBigDecimal(depreciationDetailsMap.get("AdditionsGrThan180Days"));
	                additionsLessThan180Days = parseBigDecimal(depreciationDetailsMap.get("AdditionsLessThan180Days"));
	                realizationTotalPeriod = parseBigDecimal(depreciationDetailsMap.get("RealizationTotalPeriod"));
	                realizationPeriodLessThan180Days = parseBigDecimal(depreciationDetailsMap.get("RealizationPeriodLessThan180days"));
	                netAggregateDepreciation = parseBigDecimal(depreciationDetailsMap.get("NetAggregateDepreciation"));
	                wdvLastDay = parseBigDecimal(depreciationDetailsMap.get("WDVLastDay"));
	            } else {
	                wdvFirstDay = parseBigDecimal(depreciationDetailMap.get("WDVFirstDay"));
	                additionsGrThan180Days = parseBigDecimal(depreciationDetailMap.get("AdditionsGrThan180Days"));
	                additionsLessThan180Days = parseBigDecimal(depreciationDetailMap.get("AdditionsLessThan180Days"));
	                realizationTotalPeriod = parseBigDecimal(depreciationDetailMap.get("RealizationTotalPeriod"));
	                realizationPeriodLessThan180Days = parseBigDecimal(depreciationDetailMap.get("RealizationPeriodLessThan180days"));
	                netAggregateDepreciation = parseBigDecimal(depreciationDetailMap.get("NetAggregateDepreciation"));
	                wdvLastDay = parseBigDecimal(depreciationDetailMap.get("WDVLastDay"));
	            }

	            BigDecimal totalAddition = additionsGrThan180Days.add(additionsLessThan180Days);
	            BigDecimal totalRealization = realizationTotalPeriod.add(realizationPeriodLessThan180Days);
	            BigDecimal depreciationAmount = wdvFirstDay.add(totalAddition).subtract(totalRealization);
	            if (wdvFirstDay.compareTo(BigDecimal.ZERO) != 0 ||
	            	    totalAddition.compareTo(BigDecimal.ZERO) != 0 ||
	            	    totalRealization.compareTo(BigDecimal.ZERO) != 0 ||
	            	    depreciationAmount.compareTo(BigDecimal.ZERO) != 0 ||
	            	    netAggregateDepreciation.compareTo(BigDecimal.ZERO) != 0 ||
	            	    wdvLastDay.compareTo(BigDecimal.ZERO) != 0) {
	            depreciationDTO.setWdvFirstDay(getBigDecimalValue(depreciationDTO.getWdvFirstDay(), wdvFirstDay));
	            depreciationDTO.setAdditionDuringTheYear(getBigDecimalValue(depreciationDTO.getAdditionDuringTheYear(), totalAddition));
	            depreciationDTO.setRealizationTotalPeriod(getBigDecimalValue(depreciationDTO.getRealizationTotalPeriod(), totalRealization));
	            depreciationDTO.setDepreciationAmount(getBigDecimalValue(depreciationDTO.getDepreciationAmount(), depreciationAmount));
	            depreciationDTO.setDepreciation(getBigDecimalValue(depreciationDTO.getDepreciation(), netAggregateDepreciation));
	            depreciationDTO.setWdvLastDay(getBigDecimalValue(depreciationDTO.getWdvLastDay(), wdvLastDay));

	            if (newAssetGroup != null) assetMap.put(newAssetGroup, depreciationDTO);
	            }
	        }
	    }
	}


	    private static BigDecimal parseBigDecimal(Object value) {
	        if (value == null) {
	            return BigDecimal.ZERO;
	        } else if (value instanceof BigDecimal) {
	            return (BigDecimal) value;
	        } else if (value instanceof Integer) {
	            return BigDecimal.valueOf((Integer) value);
	        } else if (value instanceof Double) {
	            return BigDecimal.valueOf((Double) value);
	        } else if (value instanceof String) {
	            try {
	                return new BigDecimal((String) value);
	            } catch (NumberFormatException e) {
	                return BigDecimal.ZERO;
	            }
	        }
	        return BigDecimal.ZERO;  
	    }

		private static BigDecimal getBigDecimalValue(BigDecimal existingValue, Object newValue) {
	        if (newValue == null) {
	            return existingValue != null ? existingValue : BigDecimal.ZERO;
	        }
	        try {
	            BigDecimal newBigDecimal = new BigDecimal(newValue.toString());
	            return (existingValue != null ? existingValue : BigDecimal.ZERO).add(newBigDecimal);
	        } catch (NumberFormatException e) {
	            System.err.println("Failed to parse value: " + newValue);
	            return existingValue != null ? existingValue : BigDecimal.ZERO;
	        }
	    }

	    private static String determineAssetGroupLabel(String assetGroup, String rateKey) {
	        switch (assetGroup) {
	            case "PlantMachinery":
	                return "Plant and Machinery (at " + rateKey.replace("Rate", "") + "%)";
	            case "Building":
	                return "Land and Building (at " + rateKey.replace("Rate", "") + "%)";
	            case "Land":
	            	   return "Land and Building (at 0%)"; 
	            case "LaptopAndComputers":
	                return "Laptop and Computers";
	            case "FurnitureFittings":
	                return "Furniture and Fittings";
	            case "IntangibleAssets":
	                return "Intangible Assets";
	        }
			return null;
	    }


	private String escapeXml(Object input) {
	    if (input == null) {
	        return ""; 
	    }
	    
	    String strInput = input.toString();
	    
	     strInput.replace("&", "&amp;")
	                   .replace("<", "&lt;")
	                   .replace(">", "&gt;")
	                   .replace("\"", "&quot;")
	                   .replace("'", "&apos;")
	     .replace("%", "perc;");
	     return strInput;
	}


	private void scheduleCFLForITR3(Document itrJson) {
		List<Document> lossesToBeCarriedForward = new ArrayList<>();
		Document scheduleCFL = itrJson.get("ScheduleCFL", Document.class);
		if (scheduleCFL.containsKey("LossCFFromPrev8thYearFromAY")) {
			Document lossCFFromPrev8thYearFromAY = scheduleCFL.get("LossCFFromPrev8thYearFromAY", Document.class)
					.get("CarryFwdLossDetail", Document.class);
			Document cfl = new Document();
			cfl.put("housePropertyLosses", lossCFFromPrev8thYearFromAY.getInteger("TotalHPPTILossCF", 0));
			cfl.put("shortTermCapitalGainLosses", lossCFFromPrev8thYearFromAY.getInteger("TotalSTCGPTILossCF", 0));
			cfl.put("longTermCapitalGainLosses", lossCFFromPrev8thYearFromAY.getInteger("TotalLTCGPTILossCF", 0));
			cfl.put("businessProfessionalLoss", lossCFFromPrev8thYearFromAY.getInteger("BrtFwdBusLoss", 0));
			cfl.put("speculativeBusinessLoss", lossCFFromPrev8thYearFromAY.getInteger("LossFrmSpecBusCF", 0));
			cfl.put("year", "2016-17");
			lossesToBeCarriedForward.add(cfl);
		}

		if (scheduleCFL.containsKey("LossCFFromPrev7thYearFromAY")) {
			Document lossCFFromPrev7thYearFromAY = scheduleCFL.get("LossCFFromPrev7thYearFromAY", Document.class)
					.get("CarryFwdLossDetail", Document.class);
			Document cfl = new Document();
			cfl.put("housePropertyLosses", lossCFFromPrev7thYearFromAY.getInteger("TotalHPPTILossCF", 0));
			cfl.put("shortTermCapitalGainLosses", lossCFFromPrev7thYearFromAY.getInteger("TotalSTCGPTILossCF", 0));
			cfl.put("longTermCapitalGainLosses", lossCFFromPrev7thYearFromAY.getInteger("TotalLTCGPTILossCF", 0));
			cfl.put("businessProfessionalLoss", lossCFFromPrev7thYearFromAY.getInteger("BrtFwdBusLoss", 0));
			cfl.put("speculativeBusinessLoss", lossCFFromPrev7thYearFromAY.getInteger("LossFrmSpecBusCF", 0));
			cfl.put("year", "2017-18");
			lossesToBeCarriedForward.add(cfl);
		}

		if (scheduleCFL.containsKey("LossCFFromPrev6thYearFromAY")) {
			Document lossCFFromPrev6thYearFromAY = scheduleCFL.get("LossCFFromPrev6thYearFromAY", Document.class)
					.get("CarryFwdLossDetail", Document.class);
			Document cfl = new Document();
			cfl.put("housePropertyLosses", lossCFFromPrev6thYearFromAY.getInteger("TotalHPPTILossCF", 0));
			cfl.put("shortTermCapitalGainLosses", lossCFFromPrev6thYearFromAY.getInteger("TotalSTCGPTILossCF", 0));
			cfl.put("longTermCapitalGainLosses", lossCFFromPrev6thYearFromAY.getInteger("TotalLTCGPTILossCF", 0));
			cfl.put("businessProfessionalLoss", lossCFFromPrev6thYearFromAY.getInteger("BrtFwdBusLoss", 0));
			cfl.put("speculativeBusinessLoss", lossCFFromPrev6thYearFromAY.getInteger("LossFrmSpecBusCF", 0));
			cfl.put("year", "2018-19");
			lossesToBeCarriedForward.add(cfl);
		}

		if (scheduleCFL.containsKey("LossCFFromPrev5thYearFromAY")) {
			Document lossCFFromPrev5thYearFromAY = scheduleCFL.get("LossCFFromPrev5thYearFromAY", Document.class)
					.get("CarryFwdLossDetail", Document.class);
			Document cfl = new Document();
			cfl.put("housePropertyLosses", lossCFFromPrev5thYearFromAY.getInteger("TotalHPPTILossCF", 0));
			cfl.put("shortTermCapitalGainLosses", lossCFFromPrev5thYearFromAY.getInteger("TotalSTCGPTILossCF", 0));
			cfl.put("longTermCapitalGainLosses", lossCFFromPrev5thYearFromAY.getInteger("TotalLTCGPTILossCF", 0));
			cfl.put("businessProfessionalLoss", lossCFFromPrev5thYearFromAY.getInteger("BrtFwdBusLoss", 0));
			cfl.put("speculativeBusinessLoss", lossCFFromPrev5thYearFromAY.getInteger("LossFrmSpecBusCF", 0));
			cfl.put("year", "2019-20");
			lossesToBeCarriedForward.add(cfl);
		}

		if (scheduleCFL.containsKey("LossCFFromPrev4thYearFromAY")) {
			Document lossCFFromPre4thYearFromAY = scheduleCFL.get("LossCFFromPrev4thYearFromAY", Document.class)
					.get("CarryFwdLossDetail", Document.class);
			Document cfl = new Document();
			cfl.put("housePropertyLosses", lossCFFromPre4thYearFromAY.getInteger("TotalHPPTILossCF", 0));
			cfl.put("shortTermCapitalGainLosses", lossCFFromPre4thYearFromAY.getInteger("TotalSTCGPTILossCF", 0));
			cfl.put("longTermCapitalGainLosses", lossCFFromPre4thYearFromAY.getInteger("TotalLTCGPTILossCF", 0));
			cfl.put("businessProfessionalLoss", lossCFFromPre4thYearFromAY.getInteger("BrtFwdBusLoss", 0));
			cfl.put("speculativeBusinessLoss", lossCFFromPre4thYearFromAY.getInteger("LossFrmSpecBusCF", 0));
			cfl.put("year", "2020-21");
			lossesToBeCarriedForward.add(cfl);
		}

		if (scheduleCFL.containsKey("LossCFFromPrev3rdYearFromAY")) {
			Document lossCFFromPrev3thYearFromAY = scheduleCFL.get("LossCFFromPrev3rdYearFromAY", Document.class)
					.get("CarryFwdLossDetail", Document.class);
			Document cfl = new Document();
			cfl.put("housePropertyLosses", lossCFFromPrev3thYearFromAY.getInteger("TotalHPPTILossCF", 0));
			cfl.put("shortTermCapitalGainLosses", lossCFFromPrev3thYearFromAY.getInteger("TotalSTCGPTILossCF", 0));
			cfl.put("longTermCapitalGainLosses", lossCFFromPrev3thYearFromAY.getInteger("TotalLTCGPTILossCF", 0));
			cfl.put("businessProfessionalLoss", lossCFFromPrev3thYearFromAY.getInteger("BrtFwdBusLoss", 0));
			cfl.put("speculativeBusinessLoss", lossCFFromPrev3thYearFromAY.getInteger("LossFrmSpecBusCF", 0));
			cfl.put("year", "2016-17");
			lossesToBeCarriedForward.add(cfl);
		}

		if (scheduleCFL.containsKey("LossCFFromPrev2ndYearFromAY")) {
			Document lossCFFromPrev2thYearFromAY = scheduleCFL.get("LossCFFromPrev2ndYearFromAY", Document.class)
					.get("CarryFwdLossDetail", Document.class);
			Document cfl = new Document();
			cfl.put("housePropertyLosses", lossCFFromPrev2thYearFromAY.getInteger("TotalHPPTILossCF", 0));
			cfl.put("shortTermCapitalGainLosses", lossCFFromPrev2thYearFromAY.getInteger("TotalSTCGPTILossCF", 0));
			cfl.put("longTermCapitalGainLosses", lossCFFromPrev2thYearFromAY.getInteger("TotalLTCGPTILossCF", 0));
			cfl.put("businessProfessionalLoss", lossCFFromPrev2thYearFromAY.getInteger("BrtFwdBusLoss", 0));
			cfl.put("speculativeBusinessLoss", lossCFFromPrev2thYearFromAY.getInteger("LossFrmSpecBusCF", 0));
			cfl.put("year", "2017-18");
			lossesToBeCarriedForward.add(cfl);
		}

		if (scheduleCFL.containsKey("LossCFFromPrevYrToAY")) {
			Document lossCFFromPrevYrToAY = scheduleCFL.get("LossCFFromPrevYrToAY", Document.class)
					.get("CarryFwdLossDetail", Document.class);
			Document cfl = new Document();
			cfl.put("housePropertyLosses", lossCFFromPrevYrToAY.getInteger("TotalHPPTILossCF", 0));
			cfl.put("shortTermCapitalGainLosses", lossCFFromPrevYrToAY.getInteger("TotalSTCGPTILossCF", 0));
			cfl.put("longTermCapitalGainLosses", lossCFFromPrevYrToAY.getInteger("TotalLTCGPTILossCF", 0));
			cfl.put("businessProfessionalLoss", lossCFFromPrevYrToAY.getInteger("BrtFwdBusLoss", 0));
			cfl.put("speculativeBusinessLoss", lossCFFromPrevYrToAY.getInteger("LossFrmSpecBusCF", 0));
			cfl.put("year", "2018-19");
			lossesToBeCarriedForward.add(cfl);
		}
//		if(scheduleCFL.containsKey("CurrentAYloss")) {
//			Document CarryFwdLossDetail = scheduleCFL.get("CurrentAYloss", Document.class).get("LossSummaryDetail", Document.class);
////			Document LossCFCurrentAssmntYear2023 = scheduleCFL.get("LossCFFromPrevYrToAY", Document.class).get("CarryFwdLossDetail", Document.class);
////			Document lossCFFromPrevYrToAY = scheduleCFL.get("LossCFFromPrevYrToAY", Document.class).get("CarryFwdLossDetail", Document.class);
//			Document cfl = new Document();
//			cfl.put("housePropertyLosses", CarryFwdLossDetail.getInteger("TotalHPPTILossCF", 0));
//			cfl.put("shortTermCapitalGainLosses", CarryFwdLossDetail.getInteger("TotalSTCGPTILossCF", 0));
//			cfl.put("longTermCapitalGainLosses", CarryFwdLossDetail.getInteger("TotalLTCGPTILossCF", 0));
//			cfl.put("businessProfessionalLoss", CarryFwdLossDetail.getInteger("BrtFwdBusLoss", 0));
//			cfl.put("speculativeBusinessLoss", CarryFwdLossDetail.getInteger("BusLossOthThanSpecLossCF", 0));
//			cfl.put("year", "2024-25");
//			lossesToBeCarriedForward.add(cfl);
//		}
		if (scheduleCFL.containsKey("LossCFCurrentAssmntYear")) {
			Document CarryFwdLossDetail = scheduleCFL.get("LossCFCurrentAssmntYear", Document.class)
					.get("CarryFwdLossDetail", Document.class);
//			Document LossCFCurrentAssmntYear2023 = scheduleCFL.get("LossCFFromPrevYrToAY", Document.class).get("CarryFwdLossDetail", Document.class);
//			Document lossCFFromPrevYrToAY = scheduleCFL.get("LossCFFromPrevYrToAY", Document.class).get("CarryFwdLossDetail", Document.class);
			Document cfl = new Document();
			cfl.put("housePropertyLosses", CarryFwdLossDetail.getInteger("TotalHPPTILossCF", 0));
			cfl.put("shortTermCapitalGainLosses", CarryFwdLossDetail.getInteger("TotalSTCGPTILossCF", 0));
			cfl.put("longTermCapitalGainLosses", CarryFwdLossDetail.getInteger("TotalLTCGPTILossCF", 0));
			cfl.put("businessProfessionalLoss", CarryFwdLossDetail.getInteger("BrtFwdBusLoss", 0));
			cfl.put("speculativeBusinessLoss", CarryFwdLossDetail.getInteger("LossFrmSpecBusCF", 0));
			cfl.put("year", "2019-20");
			lossesToBeCarriedForward.add(cfl);
		}

		if (scheduleCFL.containsKey("LossCFCurrentAssmntYear2021")) {
			Document CarryFwdLossDetail = scheduleCFL.get("LossCFCurrentAssmntYear2021", Document.class)
					.get("CarryFwdLossDetail", Document.class);
//			Document LossCFCurrentAssmntYear2023 = scheduleCFL.get("LossCFFromPrevYrToAY", Document.class).get("CarryFwdLossDetail", Document.class);
//			Document lossCFFromPrevYrToAY = scheduleCFL.get("LossCFFromPrevYrToAY", Document.class).get("CarryFwdLossDetail", Document.class);
			Document cfl = new Document();
			cfl.put("housePropertyLosses", CarryFwdLossDetail.getInteger("TotalHPPTILossCF", 0));
			cfl.put("shortTermCapitalGainLosses", CarryFwdLossDetail.getInteger("TotalSTCGPTILossCF", 0));
			cfl.put("longTermCapitalGainLosses", CarryFwdLossDetail.getInteger("TotalLTCGPTILossCF", 0));
			cfl.put("businessProfessionalLoss", CarryFwdLossDetail.getInteger("BrtFwdBusLoss", 0));
			cfl.put("speculativeBusinessLoss", CarryFwdLossDetail.getInteger("LossFrmSpecBusCF", 0));
			cfl.put("year", "2020-21");
			lossesToBeCarriedForward.add(cfl);
		}
		if (scheduleCFL.containsKey("LossCFCurrentAssmntYear2022")) {
			Document CarryFwdLossDetail = scheduleCFL.get("LossCFCurrentAssmntYear2022", Document.class)
					.get("CarryFwdLossDetail", Document.class);
//			Document LossCFCurrentAssmntYear2023 = scheduleCFL.get("LossCFFromPrevYrToAY", Document.class).get("CarryFwdLossDetail", Document.class);
//			Document lossCFFromPrevYrToAY = scheduleCFL.get("LossCFFromPrevYrToAY", Document.class).get("CarryFwdLossDetail", Document.class);
			Document cfl = new Document();
			cfl.put("housePropertyLosses", CarryFwdLossDetail.getInteger("TotalHPPTILossCF", 0));
			cfl.put("shortTermCapitalGainLosses", CarryFwdLossDetail.getInteger("TotalSTCGPTILossCF", 0));
			cfl.put("longTermCapitalGainLosses", CarryFwdLossDetail.getInteger("TotalLTCGPTILossCF", 0));
			cfl.put("businessProfessionalLoss", CarryFwdLossDetail.getInteger("BrtFwdBusLoss", 0));
			cfl.put("speculativeBusinessLoss", CarryFwdLossDetail.getInteger("LossFrmSpecBusCF", 0));
			cfl.put("year", "2021-22");
			lossesToBeCarriedForward.add(cfl);
		}

		if (scheduleCFL.containsKey("LossCFCurrentAssmntYear2023")) {
			Document CarryFwdLossDetail = scheduleCFL.get("LossCFCurrentAssmntYear2023", Document.class)
					.get("CarryFwdLossDetail", Document.class);
//			Document LossCFCurrentAssmntYear2023 = scheduleCFL.get("LossCFFromPrevYrToAY", Document.class).get("CarryFwdLossDetail", Document.class);
//			Document lossCFFromPrevYrToAY = scheduleCFL.get("LossCFFromPrevYrToAY", Document.class).get("CarryFwdLossDetail", Document.class);
			Document cfl = new Document();
			cfl.put("housePropertyLosses", CarryFwdLossDetail.getInteger("TotalHPPTILossCF", 0));
			cfl.put("shortTermCapitalGainLosses", CarryFwdLossDetail.getInteger("TotalSTCGPTILossCF", 0));
			cfl.put("longTermCapitalGainLosses", CarryFwdLossDetail.getInteger("TotalLTCGPTILossCF", 0));
			cfl.put("businessProfessionalLoss", CarryFwdLossDetail.getInteger("BrtFwdBusLoss", 0));
			cfl.put("speculativeBusinessLoss", CarryFwdLossDetail.getInteger("LossFrmSpecBusCF", 0));
			cfl.put("year", "2022-23");
			lossesToBeCarriedForward.add(cfl);
		}
		if (scheduleCFL.containsKey("LossCFCurrentAssmntYear2024")) {
			Document CarryFwdLossDetail = scheduleCFL.get("LossCFCurrentAssmntYear2024", Document.class)
					.get("CarryFwdLossDetail", Document.class);
//			Document LossCFCurrentAssmntYear2023 = scheduleCFL.get("LossCFFromPrevYrToAY", Document.class).get("CarryFwdLossDetail", Document.class);
//			Document lossCFFromPrevYrToAY = scheduleCFL.get("LossCFFromPrevYrToAY", Document.class).get("CarryFwdLossDetail", Document.class);
			Document cfl = new Document();
			cfl.put("housePropertyLosses", CarryFwdLossDetail.getInteger("TotalHPPTILossCF", 0));
			cfl.put("shortTermCapitalGainLosses", CarryFwdLossDetail.getInteger("TotalSTCGPTILossCF", 0));
			cfl.put("longTermCapitalGainLosses", CarryFwdLossDetail.getInteger("TotalLTCGPTILossCF", 0));
			cfl.put("businessProfessionalLoss", CarryFwdLossDetail.getInteger("BrtFwdBusLoss", 0));
			cfl.put("speculativeBusinessLoss", CarryFwdLossDetail.getInteger("LossFrmSpecBusCF", 0));
			cfl.put("year", "2023-24");
			lossesToBeCarriedForward.add(cfl);
		}

		int housePropertyLossesSetOffDuringTheYear = 0;
		int shortTermCapitalGainLossesSetOffDuringTheYear = 0;
		int longTermCapitalGainLossesSetOffDuringTheYear = 0;
		int businessProfessionalLossesSetOffDuringTheYear = 0;
		int speculativeBusinessLossesSetOffDuringTheYear = 0;

		int housePropertyLossesToBeCarriedForward = 0;
		int shortTermCapitalGainLossesToBeCarriedForward = 0;
		int longTermCapitalGainLossesToBeCarriedForward = 0;
		int businessProfessionalLossesToBeCarriedForward = 0;
		int speculativeBusinessLossesToBeCarriedForward = 0;

		int adjHousePropertyLossesInBFLA = 0;
		int adjShortTermCapitalGainLossesInBFLA = 0;
		int adjLongTermCapitalGainLossesInBFLA = 0;
		int adjBusinessProfessionalLossesInBFLA = 0;
		int adjSpeculativeBusinessLossesInBFLA = 0;

		if (scheduleCFL.containsKey("CurrentAYloss")) {
			Document currentAYloss = scheduleCFL.get("CurrentAYloss", Document.class);
			if (currentAYloss.containsKey("LossSummaryDetail")) {
				Document lossSummaryDetail = currentAYloss.get("LossSummaryDetail", Document.class);
				housePropertyLossesSetOffDuringTheYear = lossSummaryDetail.getInteger("TotalHPPTILossCF", 0);
				shortTermCapitalGainLossesSetOffDuringTheYear = lossSummaryDetail.getInteger("TotalSTCGPTILossCF", 0);
				longTermCapitalGainLossesSetOffDuringTheYear = lossSummaryDetail.getInteger("TotalLTCGPTILossCF", 0);
				businessProfessionalLossesSetOffDuringTheYear = lossSummaryDetail.getInteger("BusLossOthThanSpecLossCF",
						0);
				speculativeBusinessLossesSetOffDuringTheYear = lossSummaryDetail.getInteger("LossFrmSpecBusCF", 0);
			}
		}

		if (scheduleCFL.containsKey("TotalLossCFSummary")) {
			Document totalLossCFSummary = scheduleCFL.get("TotalLossCFSummary", Document.class);
			if (totalLossCFSummary.containsKey("LossSummaryDetail")) {
				Document lossSummaryDetail = totalLossCFSummary.get("LossSummaryDetail", Document.class);
				housePropertyLossesToBeCarriedForward = lossSummaryDetail.getInteger("TotalHPPTILossCF", 0);
				shortTermCapitalGainLossesToBeCarriedForward = lossSummaryDetail.getInteger("TotalSTCGPTILossCF", 0);
				longTermCapitalGainLossesToBeCarriedForward = lossSummaryDetail.getInteger("TotalLTCGPTILossCF", 0);
				businessProfessionalLossesToBeCarriedForward = lossSummaryDetail.getInteger("BusLossOthThanSpecLossCF",
						0);
				speculativeBusinessLossesToBeCarriedForward = lossSummaryDetail.getInteger("LossFrmSpecBusCF", 0);
			}
		}
		if (scheduleCFL.containsKey("AdjTotBFLossInBFLA")) {
			Document totalLossCFSummary = scheduleCFL.get("AdjTotBFLossInBFLA", Document.class);
			if (totalLossCFSummary.containsKey("LossSummaryDetail")) {
				Document lossSummaryDetail = totalLossCFSummary.get("LossSummaryDetail", Document.class);
				adjHousePropertyLossesInBFLA = lossSummaryDetail.getInteger("TotalHPPTILossCF", 0);
				adjShortTermCapitalGainLossesInBFLA = lossSummaryDetail.getInteger("TotalSTCGPTILossCF", 0);
				adjLongTermCapitalGainLossesInBFLA = lossSummaryDetail.getInteger("TotalLTCGPTILossCF", 0);
				adjBusinessProfessionalLossesInBFLA = lossSummaryDetail.getInteger("BusLossOthThanSpecLossCF", 0);
				adjSpeculativeBusinessLossesInBFLA = lossSummaryDetail.getInteger("LossFrmSpecBusCF", 0);
			}
		}

		itrJson.put("adjHousePropertyLossesInBFLA", adjHousePropertyLossesInBFLA);
		itrJson.put("adjShortTermCapitalGainLossesInBFLA", adjShortTermCapitalGainLossesInBFLA);
		itrJson.put("adjLongTermCapitalGainLossesInBFLA", adjLongTermCapitalGainLossesInBFLA);
		itrJson.put("adjBusinessProfessionalLossesInBFLA", adjBusinessProfessionalLossesInBFLA);
		itrJson.put("adjSpeculativeBusinessLossesInBFLA", adjSpeculativeBusinessLossesInBFLA);

		itrJson.put("housePropertyLossesToBeCarriedForward", housePropertyLossesToBeCarriedForward);
		itrJson.put("shortTermCapitalGainLossesToBeCarriedForward", shortTermCapitalGainLossesToBeCarriedForward);
		itrJson.put("longTermCapitalGainLossesToBeCarriedForward", longTermCapitalGainLossesToBeCarriedForward);
		itrJson.put("businessProfessionalLossesToBeCarriedForward", businessProfessionalLossesToBeCarriedForward);
		itrJson.put("speculativeBusinessLossesToBeCarriedForward", speculativeBusinessLossesToBeCarriedForward);

		itrJson.put("housePropertyLossesSetOffDuringTheYear", housePropertyLossesSetOffDuringTheYear);
		itrJson.put("shortTermCapitalGainLossesSetOffDuringTheYear", shortTermCapitalGainLossesSetOffDuringTheYear);
		itrJson.put("longTermCapitalGainLossesSetOffDuringTheYear", longTermCapitalGainLossesSetOffDuringTheYear);
		itrJson.put("businessProfessionalLossesSetOffDuringTheYear", businessProfessionalLossesSetOffDuringTheYear);
		itrJson.put("speculativeBusinessLossesSetOffDuringTheYear", speculativeBusinessLossesSetOffDuringTheYear);
		itrJson.put("lossesToBeCarriedForward", lossesToBeCarriedForward);

	}

	private void scheduleCFL(Document itrJson) throws JsonMappingException, JsonProcessingException {
		List<Document> lossesToBeCarriedForward = new ArrayList<>();
		Document scheduleCFL = itrJson.get("ScheduleCFL", Document.class);
		if (scheduleCFL.containsKey("LossCFFromPrev8thYearFromAY")) {
			Document lossCFFromPrev8thYearFromAY = scheduleCFL.get("LossCFFromPrev8thYearFromAY", Document.class)
					.get("CarryFwdLossDetail", Document.class);
			Document cfl = new Document();
			cfl.put("housePropertyLosses", lossCFFromPrev8thYearFromAY.getInteger("TotalHPPTILossCF", 0));
			cfl.put("shortTermCapitalGainLosses", lossCFFromPrev8thYearFromAY.getInteger("TotalSTCGPTILossCF", 0));
			cfl.put("longTermCapitalGainLosses", lossCFFromPrev8thYearFromAY.getInteger("TotalLTCGPTILossCF", 0));
			cfl.put("businessProfessionalLoss", lossCFFromPrev8thYearFromAY.getInteger("BrtFwdBusLoss", 0));
			cfl.put("speculativeBusinessLoss", lossCFFromPrev8thYearFromAY.getInteger("BusLossOthThanSpecLossCF", 0));
			cfl.put("year", "2016-17");
			lossesToBeCarriedForward.add(cfl);
		}

		if (scheduleCFL.containsKey("LossCFFromPrev7thYearFromAY")) {
			Document lossCFFromPrev7thYearFromAY = scheduleCFL.get("LossCFFromPrev7thYearFromAY", Document.class)
					.get("CarryFwdLossDetail", Document.class);
			Document cfl = new Document();
			cfl.put("housePropertyLosses", lossCFFromPrev7thYearFromAY.getInteger("TotalHPPTILossCF", 0));
			cfl.put("shortTermCapitalGainLosses", lossCFFromPrev7thYearFromAY.getInteger("TotalSTCGPTILossCF", 0));
			cfl.put("longTermCapitalGainLosses", lossCFFromPrev7thYearFromAY.getInteger("TotalLTCGPTILossCF", 0));
			cfl.put("businessProfessionalLoss", lossCFFromPrev7thYearFromAY.getInteger("BrtFwdBusLoss", 0));
			cfl.put("speculativeBusinessLoss", lossCFFromPrev7thYearFromAY.getInteger("BusLossOthThanSpecLossCF", 0));
			cfl.put("year", "2017-18");
			lossesToBeCarriedForward.add(cfl);
		}

		if (scheduleCFL.containsKey("LossCFFromPrev6thYearFromAY")) {
			Document lossCFFromPrev6thYearFromAY = scheduleCFL.get("LossCFFromPrev6thYearFromAY", Document.class)
					.get("CarryFwdLossDetail", Document.class);
			Document cfl = new Document();
			cfl.put("housePropertyLosses", lossCFFromPrev6thYearFromAY.getInteger("TotalHPPTILossCF", 0));
			cfl.put("shortTermCapitalGainLosses", lossCFFromPrev6thYearFromAY.getInteger("TotalSTCGPTILossCF", 0));
			cfl.put("longTermCapitalGainLosses", lossCFFromPrev6thYearFromAY.getInteger("TotalLTCGPTILossCF", 0));
			cfl.put("businessProfessionalLoss", lossCFFromPrev6thYearFromAY.getInteger("BrtFwdBusLoss", 0));
			cfl.put("speculativeBusinessLoss", lossCFFromPrev6thYearFromAY.getInteger("BusLossOthThanSpecLossCF", 0));
			cfl.put("year", "2018-19");
			lossesToBeCarriedForward.add(cfl);
		}

		if (scheduleCFL.containsKey("LossCFFromPrev5thYearFromAY")) {
			Document lossCFFromPrev5thYearFromAY = scheduleCFL.get("LossCFFromPrev5thYearFromAY", Document.class)
					.get("CarryFwdLossDetail", Document.class);
			Document cfl = new Document();
			cfl.put("housePropertyLosses", lossCFFromPrev5thYearFromAY.getInteger("TotalHPPTILossCF", 0));
			cfl.put("shortTermCapitalGainLosses", lossCFFromPrev5thYearFromAY.getInteger("TotalSTCGPTILossCF", 0));
			cfl.put("longTermCapitalGainLosses", lossCFFromPrev5thYearFromAY.getInteger("TotalLTCGPTILossCF", 0));
			cfl.put("businessProfessionalLoss", lossCFFromPrev5thYearFromAY.getInteger("BrtFwdBusLoss", 0));
			cfl.put("speculativeBusinessLoss", lossCFFromPrev5thYearFromAY.getInteger("BusLossOthThanSpecLossCF", 0));
			cfl.put("year", "2019-20");
			lossesToBeCarriedForward.add(cfl);
		}

		if (scheduleCFL.containsKey("LossCFFromPrev4thYearFromAY")) {
			Document lossCFFromPre4thYearFromAY = scheduleCFL.get("LossCFFromPrev4thYearFromAY", Document.class)
					.get("CarryFwdLossDetail", Document.class);
			Document cfl = new Document();
			cfl.put("housePropertyLosses", lossCFFromPre4thYearFromAY.getInteger("TotalHPPTILossCF", 0));
			cfl.put("shortTermCapitalGainLosses", lossCFFromPre4thYearFromAY.getInteger("TotalSTCGPTILossCF", 0));
			cfl.put("longTermCapitalGainLosses", lossCFFromPre4thYearFromAY.getInteger("TotalLTCGPTILossCF", 0));
			cfl.put("businessProfessionalLoss", lossCFFromPre4thYearFromAY.getInteger("BrtFwdBusLoss", 0));
			cfl.put("speculativeBusinessLoss", lossCFFromPre4thYearFromAY.getInteger("BusLossOthThanSpecLossCF", 0));
			cfl.put("year", "2020-21");
			lossesToBeCarriedForward.add(cfl);
		}

		if (scheduleCFL.containsKey("LossCFFromPrev3rdYearFromAY")) {
			Document lossCFFromPrev3thYearFromAY = scheduleCFL.get("LossCFFromPrev3rdYearFromAY", Document.class)
					.get("CarryFwdLossDetail", Document.class);
			Document cfl = new Document();
			cfl.put("housePropertyLosses", lossCFFromPrev3thYearFromAY.getInteger("TotalHPPTILossCF", 0));
			cfl.put("shortTermCapitalGainLosses", lossCFFromPrev3thYearFromAY.getInteger("TotalSTCGPTILossCF", 0));
			cfl.put("longTermCapitalGainLosses", lossCFFromPrev3thYearFromAY.getInteger("TotalLTCGPTILossCF", 0));
			cfl.put("businessProfessionalLoss", lossCFFromPrev3thYearFromAY.getInteger("BrtFwdBusLoss", 0));
			cfl.put("speculativeBusinessLoss", lossCFFromPrev3thYearFromAY.getInteger("BusLossOthThanSpecLossCF", 0));
			cfl.put("year", "2021-22");
			lossesToBeCarriedForward.add(cfl);
		}

		if (scheduleCFL.containsKey("LossCFFromPrev2ndYearFromAY")) {
			Document lossCFFromPrev2thYearFromAY = scheduleCFL.get("LossCFFromPrev2ndYearFromAY", Document.class)
					.get("CarryFwdLossDetail", Document.class);
			Document cfl = new Document();
			cfl.put("housePropertyLosses", lossCFFromPrev2thYearFromAY.getInteger("TotalHPPTILossCF", 0));
			cfl.put("shortTermCapitalGainLosses", lossCFFromPrev2thYearFromAY.getInteger("TotalSTCGPTILossCF", 0));
			cfl.put("longTermCapitalGainLosses", lossCFFromPrev2thYearFromAY.getInteger("TotalLTCGPTILossCF", 0));
			cfl.put("businessProfessionalLoss", lossCFFromPrev2thYearFromAY.getInteger("BrtFwdBusLoss", 0));
			cfl.put("speculativeBusinessLoss", lossCFFromPrev2thYearFromAY.getInteger("BusLossOthThanSpecLossCF", 0));
			cfl.put("year", "2022-23");
			lossesToBeCarriedForward.add(cfl);
		}

		if (scheduleCFL.containsKey("LossCFFromPrevYrToAY")) {
			Document lossCFFromPrevYrToAY = scheduleCFL.get("LossCFFromPrevYrToAY", Document.class)
					.get("CarryFwdLossDetail", Document.class);
			Document cfl = new Document();
			cfl.put("housePropertyLosses", lossCFFromPrevYrToAY.getInteger("TotalHPPTILossCF", 0));
			cfl.put("shortTermCapitalGainLosses", lossCFFromPrevYrToAY.getInteger("TotalSTCGPTILossCF", 0));
			cfl.put("longTermCapitalGainLosses", lossCFFromPrevYrToAY.getInteger("TotalLTCGPTILossCF", 0));
			cfl.put("businessProfessionalLoss", lossCFFromPrevYrToAY.getInteger("BrtFwdBusLoss", 0));
			cfl.put("speculativeBusinessLoss", lossCFFromPrevYrToAY.getInteger("BusLossOthThanSpecLossCF", 0));
			cfl.put("year", "2023-24");
			lossesToBeCarriedForward.add(cfl);
		}

		int housePropertyLossesSetOffDuringTheYear = 0;
		int shortTermCapitalGainLossesSetOffDuringTheYear = 0;
		int longTermCapitalGainLossesSetOffDuringTheYear = 0;
		int businessProfessionalLossesSetOffDuringTheYear = 0;
		int speculativeBusinessLossesSetOffDuringTheYear = 0;

		int housePropertyLossesToBeCarriedForward = 0;
		int shortTermCapitalGainLossesToBeCarriedForward = 0;
		int longTermCapitalGainLossesToBeCarriedForward = 0;
		int businessProfessionalLossesToBeCarriedForward = 0;
		int speculativeBusinessLossesToBeCarriedForward = 0;

		if (scheduleCFL.containsKey("CurrentAYloss")) {
			Document currentAYloss = scheduleCFL.get("CurrentAYloss", Document.class);
			if (currentAYloss.containsKey("LossSummaryDetail")) {
				Document lossSummaryDetail = currentAYloss.get("LossSummaryDetail", Document.class);
				housePropertyLossesSetOffDuringTheYear = lossSummaryDetail.getInteger("TotalHPPTILossCF", 0);
				shortTermCapitalGainLossesSetOffDuringTheYear = lossSummaryDetail.getInteger("TotalSTCGPTILossCF", 0);
				longTermCapitalGainLossesSetOffDuringTheYear = lossSummaryDetail.getInteger("TotalLTCGPTILossCF", 0);
				businessProfessionalLossesSetOffDuringTheYear = lossSummaryDetail.getInteger("BusLossOthThanSpecLossCF",
						0);
				speculativeBusinessLossesSetOffDuringTheYear = lossSummaryDetail.getInteger("LossFrmSpecBusCF", 0);
			}
		}

		if (scheduleCFL.containsKey("TotalLossCFSummary")) {
			Document totalLossCFSummary = scheduleCFL.get("TotalLossCFSummary", Document.class);
			if (totalLossCFSummary.containsKey("LossSummaryDetail")) {
				Document lossSummaryDetail = totalLossCFSummary.get("LossSummaryDetail", Document.class);
				housePropertyLossesToBeCarriedForward = lossSummaryDetail.getInteger("TotalHPPTILossCF", 0);
				shortTermCapitalGainLossesToBeCarriedForward = lossSummaryDetail.getInteger("TotalSTCGPTILossCF", 0);
				longTermCapitalGainLossesToBeCarriedForward = lossSummaryDetail.getInteger("TotalLTCGPTILossCF", 0);
				businessProfessionalLossesToBeCarriedForward = lossSummaryDetail.getInteger("BusLossOthThanSpecLossCF",
						0);
				speculativeBusinessLossesToBeCarriedForward = lossSummaryDetail.getInteger("LossFrmSpecBusCF", 0);
			}
		}

		itrJson.put("housePropertyLossesToBeCarriedForward", housePropertyLossesToBeCarriedForward);
		itrJson.put("shortTermCapitalGainLossesToBeCarriedForward", shortTermCapitalGainLossesToBeCarriedForward);
		itrJson.put("longTermCapitalGainLossesToBeCarriedForward", longTermCapitalGainLossesToBeCarriedForward);
		itrJson.put("businessProfessionalLossesToBeCarriedForward", businessProfessionalLossesToBeCarriedForward);
		itrJson.put("speculativeBusinessLossesToBeCarriedForward", speculativeBusinessLossesToBeCarriedForward);

		itrJson.put("housePropertyLossesSetOffDuringTheYear", housePropertyLossesSetOffDuringTheYear);
		itrJson.put("shortTermCapitalGainLossesSetOffDuringTheYear", shortTermCapitalGainLossesSetOffDuringTheYear);
		itrJson.put("longTermCapitalGainLossesSetOffDuringTheYear", longTermCapitalGainLossesSetOffDuringTheYear);
		itrJson.put("businessProfessionalLossesSetOffDuringTheYear", businessProfessionalLossesSetOffDuringTheYear);
		itrJson.put("speculativeBusinessLossesSetOffDuringTheYear", speculativeBusinessLossesSetOffDuringTheYear);
		itrJson.put("lossesToBeCarriedForward", lossesToBeCarriedForward);
	}

	private void schedule80G(Document itrJson, List<Document> donations) {
		Document schedule80G = itrJson.get("Schedule80G", Document.class);
		if (schedule80G != null) {
			Document don100Percent = schedule80G.get("Don100Percent", Document.class);
			if (don100Percent != null && don100Percent.containsKey("DoneeWithPan"))
				donations.addAll(don100Percent.getList("DoneeWithPan", Document.class));
			Document don50PercentNoApprReqd = schedule80G.get("Don50PercentNoApprReqd", Document.class);
			if (don50PercentNoApprReqd != null && don50PercentNoApprReqd.containsKey("DoneeWithPan"))
				donations.addAll(don50PercentNoApprReqd.getList("DoneeWithPan", Document.class));
			Document don100PercentApprReqd = schedule80G.get("Don100PercentApprReqd", Document.class);
			if (don100PercentApprReqd != null && don100PercentApprReqd.containsKey("DoneeWithPan"))
				donations.addAll(don100PercentApprReqd.getList("DoneeWithPan", Document.class));
			Document don50PercentApprReqd = schedule80G.get("Don50PercentApprReqd", Document.class);
			if (don50PercentApprReqd != null && don50PercentApprReqd.containsKey("DoneeWithPan"))
				donations.addAll(don50PercentApprReqd.getList("DoneeWithPan", Document.class));
		}

	}

	private String getSalDescName(String variableName) {

		String natureDescName = "";
		switch (variableName) {

		case "1":
			natureDescName = "Basic Salary";
			break;
		case "2":
			natureDescName = " Dearness Allowance";
			break;
		case "3":
			natureDescName = "Conveyance Allowance";
			break;
		case "4":
			natureDescName = "House Rent Allowance";
			break;
		case "5":
			natureDescName = "Leave Travel Allowance";
			break;
		case "6":
			natureDescName = "Children Education Allowance";
			break;
		case "7":
			natureDescName = "Other Allowance";
			break;
		case "8":
			natureDescName = "The contribution made by the Employer towards pension scheme as referred u/s 80CCD";
			break;
		case "9":
			natureDescName = "Amount deemed to be income under rule 11 of Fourth Schedule";
			break;
		case "10":
			natureDescName = "Amount deemed to be income under rule 6 of Fourth Schedule";
			break;
		case "11":
			natureDescName = "Annuity or pension";
			break;
		case "12":
			natureDescName = "Commuted Pension";
			break;
		case "13":
			natureDescName = "Gratuity";
			break;
		case "14":
			natureDescName = "Fees/ commission";
			break;
		case "15":
			natureDescName = "Advance of salary";
			break;
		case "16":
			natureDescName = "Leave Encashment";
			break;
		case "17":
			natureDescName = " Contribution made by the central government towards Agnipath scheme as referred under section 80CCH";
			break;
		case "OTH":
			natureDescName = "Others";
			break;
		}
		return natureDescName;
	}

	private String getNatureOfProfitInLieuOfSalaryDescName(String variableName) {

		String natureDescName = "";
		switch (variableName) {

		case "1":
			natureDescName = "Compensation due/received by an assessee from his employer or former employer in connection with the termination of his employment or modification thereto";
			break;
		case "2":
			natureDescName = "Any payment due/received by an assessee from his employer or a former employer or from a provident or other fund, sum received under Keyman Insurance Policy including Bonus thereto";
			break;
		case "3":
			natureDescName = " Any amount due/received by assessee from any person before joining or after cessation of employment with that person";
			break;
		case "OTH":
			natureDescName = "Any Other";
			break;
		}
		return natureDescName;
	}

	private String getPerquisitesDescName(String variableName) {

		String natureDescName = "";
		switch (variableName) {

		case "1":
			natureDescName = "Accommodation";
			break;
		case "2":
			natureDescName = "Cars / Other Automotive";
			break;
		case "3":
			natureDescName = "Sweeper, gardener, watchman or personal attendant";
			break;
		case "4":
			natureDescName = "Gas, electricity, water";
			break;
		case "5":
			natureDescName = "Interest free or concessional loans";
			break;
		case "6":
			natureDescName = "Holiday expenses";
			break;
		case "7":
			natureDescName = "Free or concessional travel";
			break;
		case "8":
			natureDescName = "Free meals";
			break;
		case "9":
			natureDescName = "Free education";
			break;
		case "10":
			natureDescName = "Gifts, vouchers, etc";
			break;
		case "11":
			natureDescName = "Credit card expenses";
			break;
		case "12":
			natureDescName = "Club expenses";
			break;
		case "13":
			natureDescName = "Use of movable assets by employees";
			break;
		case "14":
			natureDescName = "Transfer of assets to employee";
			break;
		case "15":
			natureDescName = "Value of any other benefit/amenity/service/privilege";
			break;
		case "16":
			natureDescName = "Stock options allotted or transferred by employer being an eligible start-up referred to in section 80-IAC-Tax to be deferred";
			break;
		case "17":
			natureDescName = "Stock options (non-qualified options) other than ESOP in col 16 above";
			break;
		case "18":
			natureDescName = "Contribution by employer to fund and scheme taxable under section 17(2)(vii)";
			break;
		case "19":
			natureDescName = " Annual accretion by way of interest, dividend etc. to the balance at the credit of fund and scheme referred to in section 17(2)(vii) and taxable under section 17(2)(viia)";
			break;
		case "21":
			natureDescName = "Stock options allotted or transferred by employer being an eligible start-up referred to in section 80-IAC-Tax not to be deferred";
			break;
		case "OTH":
			natureDescName = "Other benefits or amenities";
			break;

		}
		return natureDescName;
	}

	private String getSalNatureDescName(String variableName) {

		String salNatureDescName = "";
		switch (variableName) {

		case "10(5)":
			salNatureDescName = "Leave Travel allowance";
			break;
		case "10(6)":
			salNatureDescName = "Remuneration received as an official, by whatever name called, of an embassy, high commission etc";
			break;
		case "10(7)":
			salNatureDescName = "Allowances or perquisites paid or allowed as such outside India by the Government to a citizen of India for rendering service outside India";
			break;
		case "10(10)":
			salNatureDescName = "Death-cum-retirement gratuity received";
			break;
		case "10(10A)":
			salNatureDescName = "Commuted value of pension received";
			break;
		case "10(10AA)":
			salNatureDescName = "Earned leave encashment";
			break;
		case "10(10B)(i)":
			salNatureDescName = "First proviso - Compensation limit notified by CG in the Official Gazette";
			break;
		case "10(10B)(ii)":
			salNatureDescName = "Second proviso - Compensation under scheme approved by the Central Government";
			break;
		case "110(10C)":
			salNatureDescName = "Amount received on voluntary retirement or termination of service";
			break;
		case "10(10CC)":
			salNatureDescName = "Tax paid by employer on non-monetary perquisite";
			break;
		case "10(13A)":
			salNatureDescName = "House Rent Allowance";
			break;
		case "10(14)(i)":
			salNatureDescName = "Allowances or benefits not in a nature of perquisite specifically granted and incurred in performance of duties of office or employment";
			break;
		case "10(14)(ii)":
			salNatureDescName = "Allowances or benefits not in a nature of perquisite specifically granted in performance of duties of office or employment";
			break;
		case "10(14)(i)(115BAC)":
			salNatureDescName = "Allowances referred in sub-clauses (a) to (c) of sub-rule (1) in Rule 2BB";
			break;
		case "10(14)(ii)(115BAC)":
			salNatureDescName = "Transport allowance granted to certain physically handicapped assessee";
			break;
		case "EIC":
			salNatureDescName = "Exempt income received by a judge covered under the payment of salaries to Supreme Court/High Court judges Act /Rules";
			break;
		case "OTH":
			salNatureDescName = "Any Other";
			break;
		}
		return salNatureDescName;
	}

}

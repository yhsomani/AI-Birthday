# Business Requirements Document: LeadRescue AI

Date: 2026-06-25

## Executive Summary

LeadRescue AI is an AI-assisted missed-lead and follow-up platform for small home-service businesses. The product responds to missed calls, website leads, voicemail inquiries, and text messages with fast, compliant SMS follow-up. It qualifies customers, captures job details and photos, summarizes lead intent, routes urgent work to the business owner or dispatcher, and continues follow-up until the lead books, declines, opts out, or becomes inactive.

The recommended beachhead is small U.S. home-service businesses with 1-20 employees, especially HVAC, plumbing, roofing, pest control, landscaping, cleaning, and similar service categories. These companies often depend on phone calls and texts, but many owners spend the day on job sites and cannot consistently respond within minutes. The product's core promise is simple: recover more revenue from leads the business already paid to generate.

The opportunity is attractive because the first version can be built with existing APIs rather than expensive proprietary technology. Twilio can handle SMS and phone events, calendar tools can handle booking, Stripe can handle billing, and LLM APIs can generate summaries, classify intent, draft follow-up messages, and recommend next actions. This keeps initial development complexity and capital requirements low while addressing a large, validated market.

Key market signals:

- Constant Contact research reported by Lifewire found that 27% of consumers say they never hear from an SMB again after a first visit or purchase, while 81% are open to email or text follow-up after visiting or buying.
- AP News reported U.S. Census Bureau findings that AI usage among U.S. firms remains relatively small but is growing, suggesting practical vertical AI tools still have room to penetrate underserved sectors.
- NY Post, citing Harvard Joint Center for Housing Studies forecasts, reported homeowner remodeling and repair spending expected to reach $524 billion in early 2026.
- MarketWatch reported that ServiceTitan estimates U.S. and Canadian trade services spend at about $1.5 trillion annually, with technology for trades still underpenetrated.

Sources:

- Lifewire: https://www.lifewire.com/smbs-fail-at-customer-follow-up-8714600
- AP News: https://apnews.com/article/537a4db7e33fe047963b8c26bf7c366c
- NY Post: https://nypost.com/2025/11/25/real-estate/lowes-ceo-marvin-ellison-predicts-home-renovation-on-the-rise-in-2026/
- MarketWatch: https://www.marketwatch.com/story/servicetitan-ipos-growth-story-wins-over-wall-street-with-huge-market-opportunity-to-help-tradespeople-283c5f18
- Axios: https://www.axios.com/2024/12/02/servicetitan-ipo-ratchet

## Problem Statement

Small home-service businesses lose potential revenue when they respond slowly or inconsistently to leads. The issue is not always lead generation; many businesses already pay for Google Local Services Ads, SEO, referrals, directory listings, yard signs, and social media. The issue is operational follow-through after the lead arrives.

Common pain points:

- Owners and technicians miss calls while driving, working on jobs, or speaking with other customers.
- Web-form leads sit in email inboxes without immediate response.
- Voicemails are handled at the end of the day, after the customer may have contacted competitors.
- Follow-up depends on manual memory, sticky notes, spreadsheets, or personal text threads.
- Customers often need to send photos, addresses, urgency details, access instructions, and preferred appointment windows before the business can estimate or schedule.
- Smaller operators may not want a heavy field-service-management platform, but still need a professional lead-response process.

Business impact:

- Lost booked jobs from leads that were already expensive to acquire.
- Lower conversion rates from ads and referrals.
- Owner stress from constant interruptions and missed opportunities.
- Poor customer experience when customers receive no response or inconsistent follow-up.
- Limited visibility into which lead sources and response workflows are working.

LeadRescue AI addresses this by making immediate, consistent follow-up automatic while keeping the business owner or dispatcher in control of final scheduling and customer commitments.

## Target Market and Customer Personas

### Primary Market

Small home-service businesses in the United States and Canada with:

- 1-20 employees.
- High reliance on inbound phone calls, texts, and website forms.
- Limited office/admin coverage.
- Existing spend on ads, referrals, lead marketplaces, SEO, or local reputation.
- Revenue per booked job high enough that saving one or two jobs per month can justify the product.

Initial sub-verticals:

- HVAC repair and maintenance.
- Plumbing.
- Roofing inspection and repair.
- Pest control.
- Landscaping and lawn care.
- Cleaning services.
- Appliance repair.
- Electrical repair.

### Persona 1: Owner-Operator

Profile:

- Runs a small service business with 1-5 employees.
- Answers calls personally when possible.
- Uses a mobile phone, Google Calendar, spreadsheets, or a simple job app.
- Buys tools that show direct revenue impact.

Needs:

- Capture missed calls without hiring a receptionist.
- Avoid losing jobs while on-site.
- Receive clear summaries instead of reading long text threads.
- Keep setup simple and affordable.

Buying trigger:

- Realizes ads are generating calls that are not converting.
- Misses a high-value emergency or repair lead.
- Gets overwhelmed by follow-up during busy season.

### Persona 2: Office Manager or Dispatcher

Profile:

- Handles calls, scheduling, and customer updates for a 5-20 person company.
- Uses Jobber, Housecall Pro, ServiceTitan, Google Calendar, or manual tools.
- Is responsible for keeping the schedule full.

Needs:

- See all leads in one inbox.
- Know which leads are urgent.
- Reduce repetitive follow-up.
- Keep owners and technicians informed.

Buying trigger:

- Lead volume increases beyond what one admin can handle.
- The company wants better response times without adding headcount.
- Managers want visibility into missed calls and unbooked leads.

### Persona 3: Local Marketing Agency Partner

Profile:

- Runs campaigns for contractors and local service businesses.
- Is measured on leads, booked jobs, and client retention.
- Wants tools that make ad spend look more effective.

Needs:

- Improve lead conversion for clients.
- Prove response-time and follow-up performance.
- Add a recurring revenue or implementation service.

Buying trigger:

- Client complains that ads are not working, while response handling is the real bottleneck.
- Agency wants a retention-focused add-on.

## Value Proposition

### Core Promise

LeadRescue AI helps small service businesses turn more missed or delayed leads into booked jobs by responding quickly, qualifying the customer, collecting useful job details, and reminding the business to follow up.

### Customer Benefits

- **Faster response:** Customers receive a text response within 60 seconds of a missed call or web inquiry.
- **Higher conversion:** Leads are less likely to go cold while waiting for a callback.
- **Less admin work:** AI summarizes lead intent, urgency, address, job type, and next step.
- **Better customer experience:** Customers can provide details, photos, and availability in a structured flow.
- **Simple adoption:** The product overlays existing phone, calendar, and CRM workflows instead of requiring migration.
- **Controlled AI:** Businesses can approve templates, set forbidden claims, define escalation rules, and pause automation at any time.

### Positioning Statement

For small home-service businesses that lose revenue from missed calls and slow follow-up, LeadRescue AI is a lightweight lead-response assistant that captures, qualifies, and follows up with leads by SMS. Unlike full field-service platforms or generic CRMs, LeadRescue AI focuses narrowly on speed-to-lead, follow-up consistency, and booked-job recovery without forcing an operational overhaul.

## Business Goals and Objectives

### 0-90 Day Objectives

- Interview at least 30 home-service operators or dispatchers.
- Sign 5-10 pilot customers in one or two related trades.
- Launch a concierge MVP using Twilio, hosted lead forms, manual onboarding, and AI-assisted summaries.
- Prove a median first-response time under 60 seconds.
- Convert at least 5 pilots to paid accounts.
- Document at least 3 customer examples where the product recovered or advanced a lead.

### 3-6 Month Objectives

- Reach 50 paying customers.
- Reach $5,000-$10,000 monthly recurring revenue.
- Reduce onboarding time to under 30 minutes per account.
- Support at least two lead sources: missed calls and web forms.
- Implement self-serve billing, business profile setup, templates, opt-out handling, and lead dashboard.
- Establish repeatable sales messaging for one primary vertical.

### 6-12 Month Objectives

- Reach 250-500 paying customers.
- Maintain monthly logo churn below 4%.
- Launch partner program for local marketing agencies.
- Add integrations for common calendars, website forms, and lightweight CRMs.
- Build reporting that ties response speed, follow-up status, and booked appointments together.

### Strategic Objectives

- Own the "missed lead rescue" workflow for small service businesses.
- Build enough lead-context data to improve templates, routing, and conversion recommendations by trade.
- Expand from lead follow-up into adjacent revenue workflows such as quote reminders, reactivation campaigns, maintenance reminders, and review generation.

## Revenue Model

LeadRescue AI should use a subscription model with messaging usage pass-through or markup. The buyer should be able to justify the product if it helps recover one meaningful job per month.

| Plan | Price | Target Customer | Included Features |
| --- | ---: | --- | --- |
| Starter | $59/month + usage | Solo operator | 1 phone line, missed-call SMS, lead inbox, basic AI summary, simple follow-up sequence. |
| Pro | $149/month + usage | Small team | Multiple lead sources, photo requests, calendar handoff, AI qualification, custom templates, owner/admin alerts. |
| Team | $299/month + usage | Growing service business | Multiple users, routing rules, reporting, CRM export, priority onboarding, agency/client view. |
| Setup | $299 one-time | All plans | Phone setup, templates, business profile, first workflow, compliance checklist, launch support. |

Additional revenue options:

- Usage markup on SMS, MMS, and phone-number costs.
- White-label or managed accounts for local marketing agencies.
- Done-for-you onboarding package.
- Premium integrations.
- Seasonal campaign add-ons for maintenance reminders, reactivation, or review generation.

Pricing tests:

- Test $99/month flat pricing for early pilots to simplify sales.
- Test "booked job guarantee" offers, such as "first month free unless at least three qualified leads engage."
- Test agency wholesale pricing when one partner can onboard multiple clients.

## Competitive Analysis

| Competitor Category | Examples | Strengths | Weaknesses | LeadRescue AI Differentiation |
| --- | --- | --- | --- | --- |
| Full field-service platforms | ServiceTitan, Jobber, Housecall Pro, Workiz | Deep operations, scheduling, invoicing, dispatch, reporting. | Heavier onboarding, higher cost, broader than the missed-lead problem. | Lightweight overlay focused on immediate lead response and follow-up. |
| Messaging and reputation tools | Podium, Birdeye, Broadly, Synup | Strong SMS, reviews, local reputation, customer engagement. | Often broader reputation/customer communication tools; may not focus on trade-specific lead qualification. | Trade-specific missed-call recovery, job intake, and booked-job workflow. |
| Generic CRMs and automation | HubSpot, Zoho, Pipedrive, Zapier | Flexible, established, integration-rich. | Requires configuration and admin discipline; not built for small contractors by default. | Prebuilt flows, templates, and AI summaries for service businesses. |
| Virtual receptionist services | Ruby, Smith.ai, call centers | Human call handling, live coverage. | Higher cost, less automated, may not integrate with lead follow-up. | Lower-cost automated first response with escalation to humans when needed. |
| DIY texting/manual process | Mobile phone, Google Voice, spreadsheets | Familiar and cheap. | Inconsistent, not measurable, depends on owner memory. | Automated, trackable, and always-on follow-up. |

Competitive strategy:

- Avoid competing head-on with full field-service systems.
- Integrate with or export to existing tools where possible.
- Win on speed, ease, setup quality, and direct revenue language.
- Use vertical templates to feel more relevant than generic AI assistants.

## Go-to-Market Strategy

### Beachhead Strategy

Start with one narrow service category and one geography, such as HVAC/plumbing contractors in a specific metro area. Use that segment to validate messaging, collect testimonials, tune templates, and refine onboarding.

### Sales Motion

Primary sales motion: founder-led outbound and referral-driven selling.

Actions:

- Build a list of 300 local service businesses with visible phone numbers and website forms.
- Prioritize companies running ads, showing high review volume, or operating after-hours/emergency services.
- Send direct outreach around missed-call recovery and response speed.
- Offer a 14-day pilot with setup included.
- Review each customer's missed-call and lead response workflow during onboarding.
- Ask pilots to forward missed calls or add a tracked number to a specific campaign.

### Channels

- Direct cold email and phone outreach.
- Local SEO and Google Business Profile agency partnerships.
- Facebook groups and trade communities.
- Local chamber of commerce or trade association introductions.
- YouTube/TikTok demos showing real lead-recovery workflows.
- Partner packages for web designers and ad agencies serving contractors.

### Launch Offer

Recommended initial offer:

"We respond to missed calls and web leads in under 60 seconds, qualify the customer by text, and send you a clean job summary. Try it for 14 days. If no qualified lead engages through the system, you do not pay for the first month."

### Messaging Pillars

- Stop losing jobs while you are on another job.
- Get back to every lead in under 60 seconds.
- Recover more revenue from the leads you already pay for.
- No new CRM migration required.
- Your templates, your tone, your rules.

### Sales Assets Needed

- One-page ROI calculator.
- Demo video showing missed call to SMS to booked appointment.
- Vertical landing page for first trade.
- Onboarding checklist.
- Case-study template.
- Compliance and opt-out explanation.
- Agency partner one-pager.

## Key Success Metrics

### Acquisition Metrics

- Website visitor to demo-booking conversion rate.
- Outbound reply rate.
- Demo to pilot conversion rate.
- Pilot to paid conversion rate.
- Customer acquisition cost by channel.

### Activation Metrics

- Time from signup to first connected lead source.
- Time from signup to first automated SMS sent.
- Percentage of accounts with approved templates.
- Percentage of accounts with business profile completed.
- Percentage of accounts that process at least one real lead in first 7 days.

### Product Metrics

- Median first-response time.
- Lead engagement rate after automated response.
- Percentage of leads with complete job details.
- Percentage of leads routed to owner/admin.
- Percentage of leads that book, request quote, decline, opt out, or go inactive.
- AI classification accuracy from user-reviewed samples.
- AI message edit/override rate.
- Failed message rate.

### Business Metrics

- Monthly recurring revenue.
- Gross margin after messaging and AI usage costs.
- Average revenue per account.
- Net revenue retention.
- Logo churn.
- Support tickets per active account.
- Setup time per account.

### Customer Outcome Metrics

- Booked jobs influenced by LeadRescue AI.
- Estimated revenue recovered.
- Missed calls followed up within target time.
- Customer satisfaction after SMS interaction.
- Owner/admin hours saved per month.

## Risks and Mitigation Plans

| Risk | Impact | Mitigation |
| --- | --- | --- |
| SMS compliance mistakes | Legal, carrier, and trust risk. | Require opt-out handling, STOP/HELP support, consent language, message logs, quiet hours, and legal review of launch flows. |
| AI sends incorrect or overpromising messages | Customer trust and operational risk. | Use approved templates, strict business profile constraints, no autonomous pricing promises, confidence thresholds, human escalation, and message audit logs. |
| Contractors resist new software | Slow adoption. | Keep onboarding concierge-led, integrate with current phone/calendar tools, and avoid CRM migration. |
| Messaging costs reduce margin | Lower profitability at scale. | Track usage by account, charge usage separately, set fair-use limits, and optimize message sequences. |
| Existing platforms copy the feature | Competitive pressure. | Focus on underserved small operators, faster onboarding, vertical templates, agency channels, and best-in-class missed-lead workflow. |
| Poor lead quality from customer ad channels | Customers may blame product. | Report lead source, qualification status, and engagement so customers distinguish lead quality from response quality. |
| Calendar/CRM integration complexity | Slower development. | Start with scheduling links and CSV/export; add deeper integrations after paid validation. |
| Seasonal demand variation | Churn or variable usage. | Add seasonal maintenance reminders, reactivation campaigns, and review requests to maintain year-round value. |
| Business owners ignore alerts | Leads still do not close. | Add escalation rules, daily digest, reminders, and status tracking. |
| Data privacy concerns | Trust and sales friction. | Encrypt sensitive data, limit retention, provide deletion controls, and publish a plain-language privacy policy. |

## Business Requirements Summary

LeadRescue AI should begin as a narrow, practical, revenue-focused product. The business should avoid broad AI receptionist promises, generic CRM positioning, and deep field-service-management scope in the first release. The first milestone is proving that small service businesses will pay for fast, reliable, compliant lead response and follow-up because it helps them convert more existing demand into booked work.

{id:'r7',merchant:'Amazon Marketplace',initials:'AM',amount:-72.14,when:'Jul 11 · 9:42 PM',account:'Possible: Capital One •• 9380',reason:'Account check',type:'merchant',question:'Which account was used?',hint:'The sender is recognized, but the account hint was incomplete.',choices:[['Capital One •• 9380','CO','Suggested account'],['Chase •• 4471','CH','Other card'],['No account','—','Leave unassigned']],selected:'Capital One •• 9380',remember:'Use this sender for Capital One •• 9380',evidence:'CAPONE: Purchase $72.14 AMAZON MKTPLACE. Card ending **80.'}
];
const trendData={
All:{3:[2010,2007,1843],6:[1765,1920,1868,2044,2007,1843],12:[1580,1662,1725,1690,1812,1765,1920,1868,2044,2007,1843,1843]},
Groceries:{3:[631,618,589],6:[512,604,578,631,618,589],12:[480,501,530,520,498,512,604,578,631,618,589,589]},
Dining:{3:[355,362,387],6:[298,312,340,355,362,387],12:[278,287,301,295,290,298,312,340,355,362,387,387]},
Transport:{3:[248,247,258],6:[220,231,240,248,247,258],12:[205,214,225,219,216,220,231,240,248,247,258,258]},
Shopping:{3:[218,214,203],6:[240,232,226,218,214,203],12:[270,255,248,245,243,240,232,226,218,214,203,203]}
};
let selected=0,mode='share',filter='all',query='',newest=true,currentReview=0,insightCategory='All',insightRange=6,insightMeasure='amount';const resolved=new Set();const $=id=>document.getElementById(id),money=n=>new Intl.NumberFormat('en-US',{style:'currency',currency:'USD'}).format(Math.abs(n));let timer;
